package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveId
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private val AVKLARINGSBEHOV_FOR_LOKAL_SAKSBEHANDLER =
    setOf(
        Definisjon.AVKLAR_SYKDOM,
        Definisjon.AVKLAR_BISTANDSBEHOV,
        Definisjon.FASTSETT_ARBEIDSEVNE,
        Definisjon.FRITAK_MELDEPLIKT
    ).map { AvklaringsbehovKode(it.kode)}.toSet()

private val AVKLARINGSBEHOV_FOR_NAY_SAKSBEHANDLER =
    setOf(
        Definisjon.AVKLAR_STUDENT,
        Definisjon.FORESLÅ_VEDTAK,
        Definisjon.AVKLAR_SYKEPENGEERSTATNING,
        Definisjon.AVKLAR_BARNETILLEGG,
        Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT
    ).map { AvklaringsbehovKode(it.kode)}.toSet()

private val ÅPNE_STATUSER = setOf(
    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_BESLUTTER,
    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
)

private val AVSLUTTEDE_STATUSER = setOf(
    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.KVALITETSSIKRET,
    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.TOTRINNS_VURDERT,
)

class OppdaterOppgaveService(private val connection: DBConnection) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    fun oppdaterOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse) {
        val oppgaveRepo = OppgaveRepository(connection)
        val eksisterendeOppgaver = oppgaveRepo.hentOppgaver(behandlingFlytStoppetHendelse.saksnummer.toString(), behandlingFlytStoppetHendelse.referanse.referanse, null)

        val oppgaveMap = eksisterendeOppgaver.associateBy( {AvklaringsbehovKode(it.avklaringsbehovKode)}, {it} )

        when (behandlingFlytStoppetHendelse.status) {
            Status.AVSLUTTET -> avslutteOppgaver(eksisterendeOppgaver, oppgaveRepo)
            else -> oppdaterOppgaver(behandlingFlytStoppetHendelse, oppgaveMap, oppgaveRepo)
        }
    }

    private fun oppdaterOppgaver(
        behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse,
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        oppgaveRepo: OppgaveRepository
    ) {
        val åpneAvklaringsbehov = behandlingFlytStoppetHendelse.avklaringsbehov.filter {it.status in ÅPNE_STATUSER}
        val avsluttedeAvklaringsbehov = behandlingFlytStoppetHendelse.avklaringsbehov.filter {it.status in AVSLUTTEDE_STATUSER}

        // Opprett nye oppgaver
        val avklarsbehovSomDetSkalOpprettesOppgaverFor = åpneAvklaringsbehov.filter { oppgaveMap[AvklaringsbehovKode(it.definisjon.type)] == null}
        opprettOppgaver(behandlingFlytStoppetHendelse, avklarsbehovSomDetSkalOpprettesOppgaverFor, oppgaveRepo)

        // Gjenåpne avsluttede oppgaver
        åpneAvklaringsbehov.forEach { avklaringsbehov ->
            gjenåpneOppgave(oppgaveMap, avklaringsbehov, oppgaveRepo)
        }

        // Avslutt oppgaver hvor avklaringsbehovet er lukket
        val oppgaverSomSkalAvsluttes = avsluttedeAvklaringsbehov.mapNotNull { oppgaveMap[AvklaringsbehovKode(it.definisjon.type)] }
        avslutteOppgaver(oppgaverSomSkalAvsluttes, oppgaveRepo)
    }

    private fun gjenåpneOppgave(
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        avklaringsbehov: AvklaringsbehovHendelseDto,
        oppgaveRepo: OppgaveRepository
    ) {
        val eksisterendeOppgave = oppgaveMap[AvklaringsbehovKode(avklaringsbehov.definisjon.type)]
        if (eksisterendeOppgave != null && eksisterendeOppgave.status == no.nav.aap.oppgave.verdityper.Status.AVSLUTTET) {
            oppgaveRepo.gjenåpneOppgave(OppgaveId(eksisterendeOppgave.id!!), "Kelvin")
            if (avklaringsbehov.status in setOf(
                    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_BESLUTTER)
            ) {
                val sistEndretAv = avklaringsbehov.sistEndretAv()
                if (sistEndretAv != null && sistEndretAv != "Kelvin") {
                    oppgaveRepo.reserverOppgave(OppgaveId(eksisterendeOppgave.id!!), "Kelvin", sistEndretAv)
                }
            }
        }
    }

    private fun opprettOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse, avklarsbehovSomDetSkalOpprettesOppgaverFor: List<AvklaringsbehovHendelseDto>, oppgaveRepo: OppgaveRepository) {
        avklarsbehovSomDetSkalOpprettesOppgaverFor.forEach { avklaringsbehovHendelseDto ->
            val nyOppgave = behandlingFlytStoppetHendelse.opprettNyOppgave(avklaringsbehovHendelseDto, "Kelvin")
            val oppgaveId = oppgaveRepo.opprettOppgave(nyOppgave)
            log.info("Ny oppgave(id=${oppgaveId.id}) ble opprettet")
            val hvemLøsteForrigeAvklaringsbehov = behandlingFlytStoppetHendelse.hvemLøsteForrigeAvklaringsbehov()
            if (hvemLøsteForrigeAvklaringsbehov != null) {
                val (forrigeAvklaringsbehovKode, hvemLøsteForrigeIdent) = hvemLøsteForrigeAvklaringsbehov
                val nyAvklaringsbehovKode = AvklaringsbehovKode(avklaringsbehovHendelseDto.definisjon.type)
                if (sammeSaksbehandlerType(forrigeAvklaringsbehovKode, nyAvklaringsbehovKode)) {
                    val avklaringsbehovReferanse = AvklaringsbehovReferanseDto(
                        saksnummer = behandlingFlytStoppetHendelse.saksnummer.toString(),
                        referanse = behandlingFlytStoppetHendelse.referanse.referanse,
                        null,
                        nyAvklaringsbehovKode.kode
                    )
                    val reserverteOppgaver = ReserverOppgaveService(connection).reserverOppgaveUtenTilgangskontroll(avklaringsbehovReferanse, hvemLøsteForrigeIdent)
                    if (reserverteOppgaver.isNotEmpty()) {
                        log.info("Ny oppgave(id=${oppgaveId.id}) ble automatisk tilordnet: $hvemLøsteForrigeIdent")
                    }
                }
            }
        }
    }

    private fun sammeSaksbehandlerType(avklaringsbehovKode1: AvklaringsbehovKode, avklaringsbehovKode2: AvklaringsbehovKode): Boolean {
        return when {
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_NAY_SAKSBEHANDLER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_NAY_SAKSBEHANDLER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_LOKAL_SAKSBEHANDLER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_LOKAL_SAKSBEHANDLER -> true
            else -> false
        }
    }

    private fun avslutteOppgaver(oppgaver: List<OppgaveDto>, oppgaveRepo: OppgaveRepository) {
        oppgaver
            .filter { it.status != no.nav.aap.oppgave.verdityper.Status.AVSLUTTET }
            .forEach { oppgaveRepo.avsluttOppgave(OppgaveId(it.id!!), "Kelvin") }
    }

    private fun BehandlingFlytStoppetHendelse.hvemLøsteForrigeAvklaringsbehov(): Pair<AvklaringsbehovKode, String>? {
        val sisteAvsluttetAvklaringsbehov = avklaringsbehov
            .filter { it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET }
            .filter {it.sistEndretAv() != null}
            .sortedBy { it.sistEndret() }
            .lastOrNull()

        if (sisteAvsluttetAvklaringsbehov == null) {
            return null
        }
        return Pair(AvklaringsbehovKode(sisteAvsluttetAvklaringsbehov.definisjon.type), sisteAvsluttetAvklaringsbehov.sistEndretAv()!!)
    }

    private fun AvklaringsbehovHendelseDto.sistEndretAv(): String? {
        return endringer
            .sortedBy { it.tidsstempel }
            .filter { it.status == this.status }
            .map { it.endretAv }
            .lastOrNull()
    }

    private fun AvklaringsbehovHendelseDto.sistEndret(): LocalDateTime {
        return endringer
            .sortedBy { it.tidsstempel }
            .filter { it.status == this.status }
            .map { it.tidsstempel }
            .last()
    }

    private fun BehandlingFlytStoppetHendelse.opprettNyOppgave(avklaringsbehov: AvklaringsbehovHendelseDto, ident: String): OppgaveDto {
        return OppgaveDto(
            saksnummer = this.saksnummer.toString(),
            behandlingRef = this.referanse.referanse,
            behandlingOpprettet = this.opprettetTidspunkt,
            avklaringsbehovKode = avklaringsbehov.definisjon.type,
            opprettetAv = ident,
            opprettetTidspunkt = LocalDateTime.now()
        )
    }
}