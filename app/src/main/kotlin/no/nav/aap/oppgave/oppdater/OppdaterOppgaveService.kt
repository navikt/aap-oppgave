package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.slf4j.LoggerFactory
import tilgang.Rolle
import java.time.LocalDateTime

private val AVKLARINGSBEHOV_FOR_VEILEDER =
    Definisjon.entries
        .filter { it.type in setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG) }
        .filter { it.løsesAv.contains(Rolle.VEILEDER) }
        .map { AvklaringsbehovKode(it.kode.name)}
        .toSet()

private val AVKLARINGSBEHOV_FOR_SAKSBEHANDLER =
    Definisjon.entries
        .filter { it.type in setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG) }
        .filter { it.løsesAv.contains(Rolle.SAKSBEHANDLER) }
        .filter { it.løsesISteg != StegType.KVALITETSSIKRING }
        .map { AvklaringsbehovKode(it.kode.name)}
        .toSet()

// TODO: Forløpig skal alle oppgaver kunne løses av samme saksbehandler. Avklarer dette senere.
private val AVKLARINGSBEHOV_FOR_POSTMOTTAK =
    no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon.entries
        .map { AvklaringsbehovKode(it.kode.name) }.toSet()

private val ÅPNE_STATUSER = setOf(
    AvklaringsbehovStatus.OPPRETTET,
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
)

private val AVSLUTTEDE_STATUSER = setOf(
    AvklaringsbehovStatus.AVSLUTTET,
    AvklaringsbehovStatus.AVBRUTT,
    AvklaringsbehovStatus.KVALITETSSIKRET,
    AvklaringsbehovStatus.TOTRINNS_VURDERT,
)

class OppdaterOppgaveService(private val connection: DBConnection, msGraphClient: IMsGraphClient) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    private val oppgaveRepository = OppgaveRepository(connection)
    private val enhetService = EnhetService(msGraphClient)

    fun oppdaterOppgaver(oppgaveOppdatering: OppgaveOppdatering) {
        val eksisterendeOppgaver = oppgaveRepository.hentOppgaver(
            oppgaveOppdatering.saksnummer,
            oppgaveOppdatering.referanse,
            oppgaveOppdatering.journalpostId
        )

        val oppgaveMap = eksisterendeOppgaver.associateBy({ AvklaringsbehovKode(it.avklaringsbehovKode) }, { it })

        when (oppgaveOppdatering.behandlingStatus) {
            BehandlingStatus.LUKKET -> avslutteOppgaver(eksisterendeOppgaver)
            else -> oppdaterOppgaver(oppgaveOppdatering, oppgaveMap)
        }
    }

    private fun oppdaterOppgaver(
        oppgaveOppdatering: OppgaveOppdatering,
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
    ) {
        val åpneAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.filter {it.status in ÅPNE_STATUSER}
        val avsluttedeAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.filter {it.status in AVSLUTTEDE_STATUSER}

        // Opprett nye oppgaver
        val avklarsbehovSomDetSkalOpprettesOppgaverFor = åpneAvklaringsbehov
            .filter { oppgaveMap[it.avklaringsbehovKode] == null}
            .map { it.avklaringsbehovKode }
        opprettOppgaver(oppgaveOppdatering, avklarsbehovSomDetSkalOpprettesOppgaverFor)

        // Gjenåpne avsluttede oppgaver
        åpneAvklaringsbehov.forEach { avklaringsbehov ->
            gjenåpneOppgave(oppgaveOppdatering, oppgaveMap, avklaringsbehov)
        }

        // Avslutt oppgaver hvor avklaringsbehovet er lukket
        val oppgaverSomSkalAvsluttes = avsluttedeAvklaringsbehov.mapNotNull { oppgaveMap[it.avklaringsbehovKode] }
        avslutteOppgaver(oppgaverSomSkalAvsluttes)
    }

    private fun gjenåpneOppgave(
        oppgaveOppdatering: OppgaveOppdatering,
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        avklaringsbehov: AvklaringsbehovHendelse,
    ) {
        val eksisterendeOppgave = oppgaveMap[avklaringsbehov.avklaringsbehovKode]
        if (eksisterendeOppgave != null && eksisterendeOppgave.status == no.nav.aap.oppgave.verdityper.Status.AVSLUTTET) {
            val enhet = enhetService.finnEnhet(oppgaveOppdatering.personIdent)
            oppgaveRepository.gjenåpneOppgave(
                oppgaveId = eksisterendeOppgave.oppgaveId(),
                ident = "Kelvin",
                personIdent = oppgaveOppdatering.personIdent,
                enhet = enhet)
            sendOppgaveStatusOppdatering(connection, eksisterendeOppgave.oppgaveId(), HendelseType.GJENÅPNET)

            if (avklaringsbehov.status in setOf(
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER)
            ) {
                val sistEndretAv = avklaringsbehov.sistEndretAv()
                if (sistEndretAv != null && sistEndretAv != "Kelvin") {
                    val avklaringsbehovReferanse = eksisterendeOppgave.tilAvklaringsbehovReferanseDto()
                    val oppdatertOppgave = oppgaveRepository.hentOppgave(avklaringsbehovReferanse)
                    if (oppdatertOppgave != null) {
                        oppgaveRepository.reserverOppgave(oppdatertOppgave.oppgaveId(), "Kelvin", sistEndretAv)
                        sendOppgaveStatusOppdatering(connection, oppdatertOppgave.oppgaveId(), HendelseType.RESERVERT)
                    } else {
                        log.warn("Fant ikke oppgave som skulle reserveres: $avklaringsbehovReferanse")
                    }
                }
            }
        }
    }

    private fun opprettOppgaver(oppgaveOppdatering: OppgaveOppdatering, avklarsbehovSomDetSkalOpprettesOppgaverFor: List<AvklaringsbehovKode>) {
        avklarsbehovSomDetSkalOpprettesOppgaverFor.forEach { avklaringsbehovKode ->
            val enhet = enhetService.finnEnhet(oppgaveOppdatering.personIdent)
            val nyOppgave = oppgaveOppdatering.opprettNyOppgave(oppgaveOppdatering.personIdent, avklaringsbehovKode, oppgaveOppdatering.behandlingstype, "Kelvin", enhet)
            val oppgaveId = oppgaveRepository.opprettOppgave(nyOppgave)
            log.info("Ny oppgave(id=${oppgaveId.id}) ble opprettet")
            sendOppgaveStatusOppdatering(connection, oppgaveId, HendelseType.OPPRETTET)

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
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_VEILEDER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_POSTMOTTAK && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_POSTMOTTAK -> true
            else -> false
        }
    }

    private fun avslutteOppgaver(oppgaver: List<OppgaveDto>) {
        oppgaver
            .filter { it.status != no.nav.aap.oppgave.verdityper.Status.AVSLUTTET }
            .forEach {
                oppgaveRepository.avsluttOppgave(it.oppgaveId(), "Kelvin")
                sendOppgaveStatusOppdatering(connection, it.oppgaveId(), HendelseType.LUKKET)
            }
    }

    private fun OppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov(): Pair<AvklaringsbehovKode, String>? {
        val sisteAvsluttetAvklaringsbehov = avklaringsbehov
            .filter { it.status == AvklaringsbehovStatus.AVSLUTTET }
            .filter { it.sistEndretAv() != null }
            .maxByOrNull { it.sistEndret() }

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

    private fun OppgaveOppdatering.opprettNyOppgave(personIdent: String?, avklaringsbehovKode: AvklaringsbehovKode, behandlingstype: Behandlingstype, ident: String, enhet: String): OppgaveDto {
        return OppgaveDto(
            personIdent = personIdent,
            saksnummer = this.saksnummer,
            behandlingRef = this.referanse,
            journalpostId = this.journalpostId,
            enhet = enhet,
            behandlingOpprettet = this.opprettetTidspunkt,
            avklaringsbehovKode = avklaringsbehovKode.kode,
            behandlingstype = behandlingstype,
            opprettetAv = ident,
            opprettetTidspunkt = LocalDateTime.now(),
        )
    }

    private fun OppgaveDto.oppgaveId() = OppgaveId(this.id!!, this.versjon)
}