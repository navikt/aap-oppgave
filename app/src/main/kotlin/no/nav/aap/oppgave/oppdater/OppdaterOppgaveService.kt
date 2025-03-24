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
import no.nav.aap.oppgave.enhet.EnhetForOppgave
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.oppfolging.VeilarbarboppfolgingKlient
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.tilgang.Rolle
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

private val AVKLARINGSBEHOV_FOR_VEILEDER =
    Definisjon.entries
        .filter { it.type in setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG) }
        .filter { it.løsesAv.contains(Rolle.SAKSBEHANDLER_OPPFOLGING) }
        .map { AvklaringsbehovKode(it.kode.name) }
        .toSet()

private val AVKLARINGSBEHOV_FOR_SAKSBEHANDLER =
    Definisjon.entries
        .asSequence()
        .filter { it.type in setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG) }
        .filter { it.løsesAv.contains(Rolle.SAKSBEHANDLER_NASJONAL) }
        .filter { it.løsesISteg != StegType.KVALITETSSIKRING }
        .map { AvklaringsbehovKode(it.kode.name) }
        .toSet()

private val AVKLARINGSBEHOV_FOR_BESLUTTER = Definisjon.entries
    .filter { it.type in setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG) }
    .filter { it.løsesAv.contains(Rolle.BESLUTTER) }
    .map { AvklaringsbehovKode(it.kode.name) }
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
    private val veilarbarboppfolgingKlient = VeilarbarboppfolgingKlient()

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
        val åpentAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.firstOrNull { it.status in ÅPNE_STATUSER }
        val avsluttedeAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.filter { it.status in AVSLUTTEDE_STATUSER }

        // Opprette eller gjenåpne oppgave
        if (åpentAvklaringsbehov != null) {
            if (oppgaveMap[åpentAvklaringsbehov.avklaringsbehovKode] == null) {
                opprettOppgaver(oppgaveOppdatering, listOf(åpentAvklaringsbehov))
            } else {
                gjenåpneOppgave(oppgaveOppdatering, oppgaveMap, åpentAvklaringsbehov)
            }
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
        if (eksisterendeOppgave != null) {
            val enhetForOppgave = enhetForOppgave(avklaringsbehov, oppgaveOppdatering)
            val veileder = if (oppgaveOppdatering.personIdent != null) {
                veilarbarboppfolgingKlient.hentVeileder(oppgaveOppdatering.personIdent)
            } else {
                null
            }
            oppgaveRepository.oppdatereOppgave(
                oppgaveId = eksisterendeOppgave.oppgaveId(),
                ident = "Kelvin",
                personIdent = oppgaveOppdatering.personIdent,
                enhet = enhetForOppgave.enhet,
                oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
                veileder = veileder,
                påVentTil = avklaringsbehov.sistePåVentTil(),
                påVentÅrsak = avklaringsbehov.sistePåVentÅrsak()
            )
            log.info("Oppdaterer oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}")
            sendOppgaveStatusOppdatering(connection, eksisterendeOppgave.oppgaveId(), HendelseType.OPPDATERT)

            if (avklaringsbehov.status in setOf(
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
                )
            ) {
                val sistEndretAv = avklaringsbehov.sistEndretAv()
                if (sistEndretAv != "Kelvin") {
                    val avklaringsbehovReferanse = eksisterendeOppgave.tilAvklaringsbehovReferanseDto()
                    val oppdatertOppgave = oppgaveRepository.hentOppgave(avklaringsbehovReferanse)
                    if (oppdatertOppgave != null) {
                        oppgaveRepository.reserverOppgave(oppdatertOppgave.oppgaveId(), "Kelvin", sistEndretAv)
                        log.info("Reserverer oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}")
                        sendOppgaveStatusOppdatering(connection, oppdatertOppgave.oppgaveId(), HendelseType.RESERVERT)
                    } else {
                        log.warn("Fant ikke oppgave som skulle reserveres: $avklaringsbehovReferanse")
                    }
                }
            }
        }
    }

    private fun opprettOppgaver(
        oppgaveOppdatering: OppgaveOppdatering,
        avklarsbehovSomDetSkalOpprettesOppgaverFor: List<AvklaringsbehovHendelse>
    ) {
        avklarsbehovSomDetSkalOpprettesOppgaverFor.forEach { avklaringsbehovHendelse ->
            val enhetForOppgave = enhetForOppgave(avklaringsbehovHendelse, oppgaveOppdatering)
            val veileder = if (oppgaveOppdatering.personIdent != null) {
                veilarbarboppfolgingKlient.hentVeileder(oppgaveOppdatering.personIdent)
            } else {
                null
            }

            val nyOppgave = oppgaveOppdatering.opprettNyOppgave(
                personIdent = oppgaveOppdatering.personIdent,
                avklaringsbehovKode = avklaringsbehovHendelse.avklaringsbehovKode,
                behandlingstype = oppgaveOppdatering.behandlingstype,
                ident = "Kelvin",
                enhet = enhetForOppgave.enhet,
                oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
                veileder = veileder,
                påVentTil = avklaringsbehovHendelse.sistePåVentTil(),
                påVentÅrsak = avklaringsbehovHendelse.sistePåVentÅrsak()
            )
            val oppgaveId = oppgaveRepository.opprettOppgave(nyOppgave)
            log.info("Ny oppgave(id=${oppgaveId.id}) ble opprettet med status ${avklaringsbehovHendelse.status}")
            sendOppgaveStatusOppdatering(connection, oppgaveId, HendelseType.OPPRETTET)

            val hvemLøsteForrigeAvklaringsbehov = oppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov()
            if (hvemLøsteForrigeAvklaringsbehov != null) {
                val (forrigeAvklaringsbehovKode, hvemLøsteForrigeIdent) = hvemLøsteForrigeAvklaringsbehov
                val nyttAvklaringsbehov = avklaringsbehovHendelse
                if (sammeSaksbehandlerType(forrigeAvklaringsbehovKode, nyttAvklaringsbehov.avklaringsbehovKode)) {
                    val avklaringsbehovReferanse = AvklaringsbehovReferanseDto(
                        saksnummer = oppgaveOppdatering.saksnummer,
                        referanse = oppgaveOppdatering.referanse,
                        null,
                        nyttAvklaringsbehov.avklaringsbehovKode.kode
                    )
                    val reserverteOppgaver = ReserverOppgaveService(connection).reserverOppgaveUtenTilgangskontroll(
                        avklaringsbehovReferanse,
                        hvemLøsteForrigeIdent
                    )
                    if (reserverteOppgaver.isNotEmpty()) {
                        log.info("Ny oppgave(id=${oppgaveId.id}) ble automatisk tilordnet: $hvemLøsteForrigeIdent")
                    }
                }
            }
        }
    }

    private fun enhetForOppgave(
        avklaringsbehovHendelse: AvklaringsbehovHendelse,
        oppgaveOppdatering: OppgaveOppdatering
    ) =
        if (avklaringsbehovHendelse.avklaringsbehovKode in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER + AVKLARINGSBEHOV_FOR_BESLUTTER) {
            // Sett til NAY-kø om avklaringsbehovet svarer til en NAY-oppgave
            EnhetForOppgave(
                "4491",
                oppfølgingsenhet = null
            )
        } else {
            enhetService.finnEnhetForOppgave(oppgaveOppdatering.personIdent)
        }

    private fun sammeSaksbehandlerType(
        avklaringsbehovKode1: AvklaringsbehovKode,
        avklaringsbehovKode2: AvklaringsbehovKode
    ): Boolean {
        return when {
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_VEILEDER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_POSTMOTTAK && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_POSTMOTTAK -> true
            else -> false
        }
    }

    private fun avslutteOppgaver(oppgaver: List<OppgaveDto>) {
        oppgaver
            .filter { it.status != Status.AVSLUTTET }
            .forEach {
                oppgaveRepository.avsluttOppgave(it.oppgaveId(), "Kelvin")
                log.info("AVsluttet oppgave med ID ${it.oppgaveId()}.")
                sendOppgaveStatusOppdatering(connection, it.oppgaveId(), HendelseType.LUKKET)
            }
    }

    private fun OppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov(): Pair<AvklaringsbehovKode, String>? {
        val sisteAvsluttetAvklaringsbehov = avklaringsbehov
            .filter { it.status == AvklaringsbehovStatus.AVSLUTTET }
            .maxByOrNull { it.sistEndret() }

        if (sisteAvsluttetAvklaringsbehov == null) {
            return null
        }
        return Pair(sisteAvsluttetAvklaringsbehov.avklaringsbehovKode, sisteAvsluttetAvklaringsbehov.sistEndretAv())
    }

    private fun AvklaringsbehovHendelse.sisteEndring(): Endring {
        return endringer
            .sortedBy { it.tidsstempel }.last { it.status == this.status }
    }

    private fun AvklaringsbehovHendelse.sistEndretAv() = sisteEndring().endretAv
    private fun AvklaringsbehovHendelse.sistEndret() = sisteEndring().tidsstempel
    private fun AvklaringsbehovHendelse.sistePåVentÅrsak() = sisteEndring().påVentÅrsak
    private fun AvklaringsbehovHendelse.sistePåVentTil() = sisteEndring().påVentTil


    private fun OppgaveOppdatering.opprettNyOppgave(
        personIdent: String?,
        avklaringsbehovKode: AvklaringsbehovKode,
        behandlingstype: Behandlingstype,
        ident: String,
        enhet: String,
        oppfølgingsenhet: String?,
        veileder: String?,
        påVentTil: LocalDate?,
        påVentÅrsak: String?
    ): OppgaveDto {
        return OppgaveDto(
            personIdent = personIdent,
            saksnummer = this.saksnummer,
            behandlingRef = this.referanse,
            journalpostId = this.journalpostId,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            veileder = veileder,
            behandlingOpprettet = this.opprettetTidspunkt,
            avklaringsbehovKode = avklaringsbehovKode.kode,
            behandlingstype = behandlingstype,
            opprettetAv = ident,
            opprettetTidspunkt = LocalDateTime.now(),
            påVentTil = påVentTil,
            påVentÅrsak = påVentÅrsak
        )
    }

    private fun OppgaveDto.oppgaveId() = OppgaveId(this.id!!, this.versjon)
}