package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.metrikker.statistikkHendelseCounter
import org.slf4j.LoggerFactory

/**
 * Dekoratør rundt [FlytJobbRepository] som samler statistikk-hendelser (se [sendOppgaveStatusOppdatering])
 * for samme oppgave i én transaksjon, i stedet for å legge dem til JOBB-tabellen med en gang.
 *
 * Bakgrunn: en oppgave kan bli oppdatert/reservert/avsluttet flere ganger i løpet av samme
 * transaksjon (f.eks. både OPPDATERT og RESERVERT), og da vil hvert kall til
 * [sendOppgaveStatusOppdatering] i dag lage en egen JOBB-rad. Det gir unødvendig mange
 * statistikk-hendelser for én reell endring.
 *
 * Denne klassen bufrer slike hendelser per oppgave (siste registrerte hendelse vinner, siden
 * den reflekterer sluttilstanden til oppgaven når transaksjonen committer), og legger dem til
 * JOBB-tabellen først når [flush] kalles - typisk helt til slutt i transaksjonsblokken, men
 * fortsatt før commit. Dette bevarer transactional outbox-egenskapen: JOBB-raden committer
 * sammen med resten av oppgave-endringene, og selve utsendingen til statistikk skjer først når
 * jobben plukkes opp og prosesseres i etterkant.
 *
 * Hver gang en bufret hendelse blir overskrevet (dedup) telles dette i Prometheus-metrikken
 * `statistikk_hendelse_totalt{resultat="dedup_forkastet"}`, tagget med hendelseType til den
 * hendelsen som ble forkastet. Se [sendOppgaveStatusOppdatering] for tellingen av totalt antall
 * forsøk (`resultat="forsøkt"`).
 *
 * Andre typer jobber enn statistikk-hendelser sendes videre til delegatet uten bufring.
 */
class DedupliserendeFlytJobbRepository(
    private val delegate: FlytJobbRepository
) : FlytJobbRepository by delegate {

    private val log = LoggerFactory.getLogger(DedupliserendeFlytJobbRepository::class.java)

    // nøkkel er oppgaveId (sak_id), verdi er siste registrerte JobbInput for den oppgaven
    private val bufredeStatistikkHendelser = linkedMapOf<Long, JobbInput>()

    override fun leggTil(jobbInput: JobbInput) {
        val oppgaveId = jobbInput.sakIdOrNull()
        if (jobbInput.type() == StatistikkHendelseJobb.type() && oppgaveId != null) {
            // put() returnerer forrige verdi for nøkkelen, altså hendelsen som forkastes fordi
            // den blir overskrevet av en nyere hendelse for samme oppgave i samme transaksjon
            val forkastetHendelse = bufredeStatistikkHendelser.put(oppgaveId, jobbInput)
            if (forkastetHendelse != null) {
                prometheus.statistikkHendelseCounter(
                    forkastetHendelse.parameter("hendelsesType"),
                    "dedup_forkastet"
                ).increment()
            }
        } else {
            delegate.leggTil(jobbInput)
        }
    }

    /**
     * Legger de bufrede statistikk-hendelsene til JOBB-tabellen, én per oppgave. Må kalles
     * eksplisitt før transaksjonen committer - typisk siste kall i transaksjonsblokken.
     */
    fun flush() {
        if (bufredeStatistikkHendelser.isNotEmpty()) {
            log.info("Flusher ${bufredeStatistikkHendelser.size} bufret(e) statistikk-hendelse(r) for oppgaver: ${bufredeStatistikkHendelser.keys}")
        }
        bufredeStatistikkHendelser.values.forEach { delegate.leggTil(it) }
        bufredeStatistikkHendelser.clear()
    }
}

/**
 * Kjører [block] med en [DedupliserendeFlytJobbRepository] og garanterer at [DedupliserendeFlytJobbRepository.flush]
 * kalles etterpå - også om [block] kaster en exception. Siden dette skal brukes inne i en
 * `dataSource.transaction { ... }`-blokk vil et eventuelt kast likevel føre til rollback av
 * transaksjonen, så en flush på feilende vei er ufarlig - JOBB-radene committer aldri.
 *
 * Bruk denne i stedet for å bygge [DedupliserendeFlytJobbRepository] og kalle `flush()` manuelt,
 * slik at man ikke kan glemme å flushe de bufrede statistikk-hendelsene.
 */
inline fun <T> bufretStatistikk(connection: DBConnection, block: (FlytJobbRepository) -> T): T {
    val flytJobbRepository = DedupliserendeFlytJobbRepository(FlytJobbRepositoryImpl(connection))
    try {
        return block(flytJobbRepository)
    } finally {
        flytJobbRepository.flush()
    }
}
