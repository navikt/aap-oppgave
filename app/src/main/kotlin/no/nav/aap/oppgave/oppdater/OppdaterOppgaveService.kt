package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.verdityper.Behandlingstype
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
    AvklaringsbehovStatus.OPPRETTET,
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
)

private val AVSLUTTEDE_STATUSER = setOf(
    AvklaringsbehovStatus.AVSLUTTET,
    AvklaringsbehovStatus.AVSLUTTET,
    AvklaringsbehovStatus.KVALITETSSIKRET,
    AvklaringsbehovStatus.TOTRINNS_VURDERT,
)

class OppdaterOppgaveService(private val connection: DBConnection) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    fun oppdaterOppgaver(oppgaveOppdatering: OppgaveOppdatering) {
        val oppgaveRepo = OppgaveRepository(connection)
        val eksisterendeOppgaver = oppgaveRepo.hentOppgaver(oppgaveOppdatering.saksnummer, oppgaveOppdatering.referanse, oppgaveOppdatering.journalpostId)

        val oppgaveMap = eksisterendeOppgaver.associateBy( {AvklaringsbehovKode(it.avklaringsbehovKode)}, {it} )

        when (oppgaveOppdatering.behandlingStatus) {
            BehandlingStatus.LUKKET -> avslutteOppgaver(eksisterendeOppgaver, oppgaveRepo)
            else -> oppdaterOppgaver(oppgaveOppdatering, oppgaveMap, oppgaveRepo)
        }
    }

    private fun oppdaterOppgaver(
        oppgaveOppdatering: OppgaveOppdatering,
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        oppgaveRepo: OppgaveRepository
    ) {
        val åpneAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.filter {it.status in ÅPNE_STATUSER}
        val avsluttedeAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.filter {it.status in AVSLUTTEDE_STATUSER}

        // Opprett nye oppgaver
        val avklarsbehovSomDetSkalOpprettesOppgaverFor = åpneAvklaringsbehov
            .filter { oppgaveMap[it.avklaringsbehovKode] == null}
            .map { it.avklaringsbehovKode }
        opprettOppgaver(oppgaveOppdatering, avklarsbehovSomDetSkalOpprettesOppgaverFor, oppgaveRepo)

        // Gjenåpne avsluttede oppgaver
        åpneAvklaringsbehov.forEach { avklaringsbehov ->
            gjenåpneOppgave(oppgaveMap, avklaringsbehov, oppgaveRepo)
        }

        // Avslutt oppgaver hvor avklaringsbehovet er lukket
        val oppgaverSomSkalAvsluttes = avsluttedeAvklaringsbehov.mapNotNull { oppgaveMap[it.avklaringsbehovKode] }
        avslutteOppgaver(oppgaverSomSkalAvsluttes, oppgaveRepo)
    }

    private fun gjenåpneOppgave(
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        avklaringsbehov: AvklaringsbehovHendelse,
        oppgaveRepo: OppgaveRepository
    ) {
        val eksisterendeOppgave = oppgaveMap[avklaringsbehov.avklaringsbehovKode]
        if (eksisterendeOppgave != null && eksisterendeOppgave.status == no.nav.aap.oppgave.verdityper.Status.AVSLUTTET) {
            oppgaveRepo.gjenåpneOppgave(eksisterendeOppgave.oppgaveId(), "Kelvin")
            if (avklaringsbehov.status in setOf(
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER)
            ) {
                val sistEndretAv = avklaringsbehov.sistEndretAv()
                if (sistEndretAv != null && sistEndretAv != "Kelvin") {
                    val avklaringsbehovReferanse = eksisterendeOppgave.tilAvklaringsbehovReferanseDto()
                    val oppdatertOppgave = oppgaveRepo.hentOppgave(avklaringsbehovReferanse)
                    if (oppdatertOppgave != null) {
                        oppgaveRepo.reserverOppgave(oppdatertOppgave.oppgaveId(), "Kelvin", sistEndretAv)
                    } else {
                        log.warn("Fant ikke oppgave som skulle reserveres: $avklaringsbehovReferanse")
                    }
                }
            }
        }
    }

    private fun opprettOppgaver(oppgaveOppdatering: OppgaveOppdatering, avklarsbehovSomDetSkalOpprettesOppgaverFor: List<AvklaringsbehovKode>, oppgaveRepo: OppgaveRepository) {
        avklarsbehovSomDetSkalOpprettesOppgaverFor.forEach { avklaringsbehovKode ->
            val nyOppgave = oppgaveOppdatering.opprettNyOppgave(oppgaveOppdatering.personIdent, avklaringsbehovKode, oppgaveOppdatering.behandlingstype, "Kelvin")
            val oppgaveId = oppgaveRepo.opprettOppgave(nyOppgave)
            log.info("Ny oppgave(id=${oppgaveId.id}) ble opprettet")
            val hvemLøsteForrigeAvklaringsbehov = oppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov()
            if (hvemLøsteForrigeAvklaringsbehov != null) {
                val (forrigeAvklaringsbehovKode, hvemLøsteForrigeIdent) = hvemLøsteForrigeAvklaringsbehov
                val nyAvklaringsbehovKode = avklaringsbehovKode
                if (sammeSaksbehandlerType(forrigeAvklaringsbehovKode, nyAvklaringsbehovKode)) {
                    val avklaringsbehovReferanse = AvklaringsbehovReferanseDto(
                        saksnummer = oppgaveOppdatering.saksnummer,
                        referanse = oppgaveOppdatering.referanse,
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
            .forEach { oppgaveRepo.avsluttOppgave(it.oppgaveId(), "Kelvin") }
    }

    private fun OppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov(): Pair<AvklaringsbehovKode, String>? {
        val sisteAvsluttetAvklaringsbehov = avklaringsbehov
            .filter { it.status == AvklaringsbehovStatus.AVSLUTTET }
            .filter {it.sistEndretAv() != null}
            .sortedBy { it.sistEndret() }
            .lastOrNull()

        if (sisteAvsluttetAvklaringsbehov == null) {
            return null
        }
        return Pair(sisteAvsluttetAvklaringsbehov.avklaringsbehovKode, sisteAvsluttetAvklaringsbehov.sistEndretAv()!!)
    }

    private fun AvklaringsbehovHendelse.sistEndretAv(): String? {
        return endringer
            .sortedBy { it.tidsstempel }
            .filter { it.status == this.status }
            .map { it.endretAv }
            .lastOrNull()
    }

    private fun AvklaringsbehovHendelse.sistEndret(): LocalDateTime {
        return endringer
            .sortedBy { it.tidsstempel }
            .filter { it.status == this.status }
            .map { it.tidsstempel }
            .last()
    }

    private fun OppgaveOppdatering.opprettNyOppgave(personIdent: String?, avklaringsbehovKode: AvklaringsbehovKode, behandlingstype: Behandlingstype, ident: String): OppgaveDto {
        return OppgaveDto(
            personIdent = personIdent,
            saksnummer = this.saksnummer,
            behandlingRef = this.referanse,
            journalpostId = this.journalpostId,
            behandlingOpprettet = this.opprettetTidspunkt,
            avklaringsbehovKode = avklaringsbehovKode.kode,
            behandlingstype = behandlingstype,
            opprettetAv = ident,
            opprettetTidspunkt = LocalDateTime.now(),
        )
    }

    private fun OppgaveDto.oppgaveId() = OppgaveId(this.id!!, this.versjon)
}