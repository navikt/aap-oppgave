package no.nav.aap.oppgave.oppdater

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.oppfolging.IVeilarbarboppfolgingKlient
import no.nav.aap.oppgave.klienter.oppfolging.VeilarbarboppfolgingKlient
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

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

class OppdaterOppgaveService(
    private val connection: DBConnection,
    msGraphClient: IMsGraphClient,
    private val veilarbarboppfolgingKlient: IVeilarbarboppfolgingKlient = VeilarbarboppfolgingKlient(),
    private val enhetService: IEnhetService = EnhetService(msGraphClient)
) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    private val oppgaveRepository = OppgaveRepository(connection)

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
        // Om det er flere åpne avklaringsbehov (f.eks ved tilbakeføring fra beslutter), velger vi det eldste avklaringsbehovet.
        // Dette burde svare til det første steget i flyten.
        // På sikt bør vi kanskje se på mer robuste løsninger, f.eks at behandlingsflyt velger ut hvilken avklaringsbehov
        // som skal løses først, i stedet for alle.
        val åpentAvklaringsbehov =
            oppgaveOppdatering.avklaringsbehov.filter { it.status in ÅPNE_STATUSER }.eldsteAvklaringsbehov()
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
            val enhetForOppgave =
                enhetService.utledEnhetForOppgave(avklaringsbehov.avklaringsbehovKode, oppgaveOppdatering.personIdent)
            val veileder = if (oppgaveOppdatering.personIdent != null) {
                veilarbarboppfolgingKlient.hentVeileder(oppgaveOppdatering.personIdent)
            } else {
                null
            }

            if (avklaringsbehov.status in setOf(
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
                )
            ) {
                if (eksisterendeOppgave.status == Status.AVSLUTTET) {
                    log.info("Gjenåpner oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}")
                    oppgaveRepository.gjenåpneOppgave(eksisterendeOppgave.oppgaveId(), "Kelvin")
                } else {
                    log.warn("Kan ikke gjenåpne oppgave som er allerede er åpen (id=${eksisterendeOppgave.oppgaveId()}, avklaringsbehov=${avklaringsbehov.avklaringsbehovKode})")
                    return
                }
                sendOppgaveStatusOppdatering(
                    eksisterendeOppgave.oppgaveId(),
                    HendelseType.OPPDATERT,
                    FlytJobbRepository(connection)
                )
                val sistEndretAv = avklaringsbehov.sistEndretAv(AvklaringsbehovStatus.AVSLUTTET)
                if (sistEndretAv != "Kelvin") {
                    val avklaringsbehovReferanse = eksisterendeOppgave.tilAvklaringsbehovReferanseDto()
                    val oppdatertOppgave = oppgaveRepository.hentOppgave(avklaringsbehovReferanse)
                    if (oppdatertOppgave != null) {
                        oppgaveRepository.reserverOppgave(oppdatertOppgave.oppgaveId(), "Kelvin", sistEndretAv)
                        log.info("Reserverer oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}")
                        sendOppgaveStatusOppdatering(
                            oppdatertOppgave.oppgaveId(),
                            HendelseType.RESERVERT,
                            FlytJobbRepository(connection)
                        )
                    } else {
                        log.warn("Fant ikke oppgave som skulle reserveres: $avklaringsbehovReferanse")
                    }
                }
            } else {
                val årsakTilSattPåVent = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent
                oppgaveRepository.oppdatereOppgave(
                    oppgaveId = eksisterendeOppgave.oppgaveId(),
                    ident = "Kelvin",
                    personIdent = oppgaveOppdatering.personIdent,
                    enhet = enhetForOppgave.enhet,
                    oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
                    veileder = veileder,
                    påVentTil = oppgaveOppdatering.venteInformasjon?.frist,
                    påVentÅrsak = årsakTilSattPåVent,
                )
                log.info("Oppdaterer oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}. Venteårsak: $årsakTilSattPåVent")
                sendOppgaveStatusOppdatering(
                    eksisterendeOppgave.oppgaveId(),
                    HendelseType.OPPDATERT,
                    FlytJobbRepository(connection)
                )
            }
        }
    }

    private fun opprettOppgaver(
        oppgaveOppdatering: OppgaveOppdatering,
        avklaringsbehovSomDetSkalOpprettesOppgaverFor: List<AvklaringsbehovHendelse>
    ) {
        avklaringsbehovSomDetSkalOpprettesOppgaverFor.forEach { avklaringsbehovHendelse ->
            val enhetForOppgave = enhetService.utledEnhetForOppgave(
                avklaringsbehovHendelse.avklaringsbehovKode,
                oppgaveOppdatering.personIdent
            )
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
                påVentTil = oppgaveOppdatering.venteInformasjon?.frist,
                påVentÅrsak = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent
            )
            val oppgaveId = oppgaveRepository.opprettOppgave(nyOppgave)
            log.info("Ny oppgave(id=${oppgaveId.id}) ble opprettet med status ${avklaringsbehovHendelse.status} for avklaringsbehov ${avklaringsbehovHendelse.avklaringsbehovKode}. Saksnummer: ${oppgaveOppdatering.saksnummer}. Venteinformasjon: ${oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent}")
            sendOppgaveStatusOppdatering(oppgaveId, HendelseType.OPPRETTET, FlytJobbRepository(connection))

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
                        log.info("Ny oppgave(id=${oppgaveId.id}) ble automatisk tilordnet: $hvemLøsteForrigeIdent. Saksnummer: ${oppgaveOppdatering.saksnummer}")
                    }
                }
            }
        }
    }

    private fun sammeSaksbehandlerType(
        avklaringsbehovKode1: AvklaringsbehovKode,
        avklaringsbehovKode2: AvklaringsbehovKode
    ): Boolean {
        return when {
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_VEILEDER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_BESLUTTER && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_BESLUTTER -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK -> true
            avklaringsbehovKode1 in AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK && avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK -> true
            else -> false
        }
    }

    private fun avslutteOppgaver(oppgaver: List<OppgaveDto>) {
        oppgaver
            .filter { it.status != Status.AVSLUTTET }
            .forEach {
                oppgaveRepository.avsluttOppgave(it.oppgaveId(), "Kelvin")
                log.info("AVsluttet oppgave med ID ${it.oppgaveId()}.")
                sendOppgaveStatusOppdatering(it.oppgaveId(), HendelseType.LUKKET, FlytJobbRepository(connection))
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

    private fun AvklaringsbehovHendelse.sisteEndring(status: AvklaringsbehovStatus = this.status): Endring {
        return endringer
            .sortedBy { it.tidsstempel }
            .last { it.status == status }
    }

    private fun AvklaringsbehovHendelse.opprettetTidspunkt(): LocalDateTime {
        return endringer.minByOrNull { it.tidsstempel }!!.tidsstempel
    }

    private fun List<AvklaringsbehovHendelse>.eldsteAvklaringsbehov(): AvklaringsbehovHendelse? {
        return this.minByOrNull { it.opprettetTidspunkt() }
    }

    private fun AvklaringsbehovHendelse.sistEndretAv(status: AvklaringsbehovStatus = this.status) =
        sisteEndring(status).endretAv

    private fun AvklaringsbehovHendelse.sistEndret() = sisteEndring().tidsstempel

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