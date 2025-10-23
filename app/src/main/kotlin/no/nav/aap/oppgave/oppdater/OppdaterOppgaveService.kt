package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.ReturInformasjon
import no.nav.aap.oppgave.ReturStatus
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.klienter.oppfolging.ISykefravarsoppfolgingGateway
import no.nav.aap.oppgave.klienter.oppfolging.IVeilarbarboppfolgingGateway
import no.nav.aap.oppgave.klienter.oppfolging.SykefravarsoppfolgingGateway
import no.nav.aap.oppgave.klienter.oppfolging.VeilarbarboppfolgingGateway
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.oppgave.ÅrsakTilReturKode
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

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

private const val KELVIN = "Kelvin"

class OppdaterOppgaveService(
    msGraphClient: IMsGraphGateway,
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService(),
    private val veilarbarboppfolgingKlient: IVeilarbarboppfolgingGateway = VeilarbarboppfolgingGateway,
    private val sykefravarsoppfolgingKlient: ISykefravarsoppfolgingGateway = SykefravarsoppfolgingGateway,
    private val enhetService: IEnhetService = EnhetService(msGraphClient),
    private val oppgaveRepository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository,
    private val mottattDokumentRepository: MottattDokumentRepository,
) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    private val reserverOppgaveService = ReserverOppgaveService(
        oppgaveRepository,
        flytJobbRepository,
    )

    fun håndterNyOppgaveOppdatering(oppgaveOppdatering: OppgaveOppdatering) {
        val eksisterendeOppgaver = oppgaveRepository.hentOppgaver(referanse = oppgaveOppdatering.referanse)
        val oppgaveMap = eksisterendeOppgaver.associateBy({ AvklaringsbehovKode(it.avklaringsbehovKode) }, { it })

        when (oppgaveOppdatering.behandlingStatus) {
            BehandlingStatus.LUKKET -> avslutteOppgaver(eksisterendeOppgaver)
            else -> oppdaterOppgaver(oppgaveOppdatering, oppgaveMap)
        }
        validerOppgaveTilstandEtterOppdatering(oppgaveOppdatering.referanse)
    }

    private fun oppdaterOppgaver(
        oppgaveOppdatering: OppgaveOppdatering,
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
    ) {
        // avklaringsbehov kommer inn i riktig rekkefølge fra behandlingsflyt. Velger det første åpne
        val åpentAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.firstOrNull { it.status in ÅPNE_STATUSER }
        val avsluttedeAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.filter { it.status in AVSLUTTEDE_STATUSER }

        if (åpentAvklaringsbehov != null) {
            val eksisterendeOppgave = oppgaveMap[åpentAvklaringsbehov.avklaringsbehovKode]
            if (eksisterendeOppgave == null) {
                opprettNyOppgaveForAvklaringsbehov(oppgaveOppdatering, oppgaveMap, åpentAvklaringsbehov)
            } else {
                if (unleashService.isEnabled(FeatureToggles.AvsluttOppgaverVedGjenaapning)) {
                    avsluttOppgaverSenereIFlyt(oppgaveMap, åpentAvklaringsbehov)
                }
                oppdaterEksistendeOppgave(oppgaveOppdatering, eksisterendeOppgave, åpentAvklaringsbehov)
            }
        }

        // Avslutt oppgaver hvor avklaringsbehovet er lukket
        val oppgaverSomSkalAvsluttes = avsluttedeAvklaringsbehov.mapNotNull { oppgaveMap[it.avklaringsbehovKode] }
        avslutteOppgaver(oppgaverSomSkalAvsluttes)
    }

    private fun oppdaterEksistendeOppgave(
        oppgaveOppdatering: OppgaveOppdatering,
        eksisterendeOppgave: OppgaveDto,
        avklaringsbehov: AvklaringsbehovHendelse,
    ) {
        // Send oppdatering til statistikk på slutten, så man ikke får 3 forskjellige oppdateringer
        if (harBlittSendtTilbakeFraKvalitetssikrer(avklaringsbehov)) {
            gjenÅpneOppgaveEtterReturFraKvalitetssikrer(eksisterendeOppgave, avklaringsbehov)
        } else {
            oppdaterOppgave(oppgaveOppdatering, avklaringsbehov, eksisterendeOppgave)
            sendOppgaveStatusOppdatering(
                eksisterendeOppgave.oppgaveId(),
                HendelseType.OPPDATERT,
                flytJobbRepository
            )
            // Hvis oppgaven ble satt på vent, reserver til saksbehandler som satte på vent
            if (oppgaveOppdatering.venteInformasjon != null && eksisterendeOppgave.påVentTil == null && eksisterendeOppgave.reservertAv == null) {
                log.info("Forsøker å reservere oppgave ${eksisterendeOppgave.oppgaveId()} til saksbehandler som satte den på vent")
                reserverOppgave(eksisterendeOppgave, oppgaveOppdatering.venteInformasjon)
            }
        }
    }

    private fun oppdaterOppgave(
        oppgaveOppdatering: OppgaveOppdatering,
        avklaringsbehov: AvklaringsbehovHendelse,
        eksisterendeOppgave: OppgaveDto
    ) {
        val personIdent = oppgaveOppdatering.personIdent
        val skalOverstyresTilLokalkontor = skalOverstyresTilLokalKontor(oppgaveOppdatering, avklaringsbehov)
        val enhetForOppgave =
            enhetService.utledEnhetForOppgave(
                avklaringsbehov.avklaringsbehovKode,
                personIdent,
                oppgaveOppdatering.relevanteIdenter,
                oppgaveOppdatering.saksnummer,
                skalOverstyresTilLokalkontor,
            )
        val veilederArbeid = if (personIdent != null) hentVeilederArbeidsoppfølging(personIdent) else null
        val veilederSykdom = if (personIdent != null) hentVeilederSykefraværoppfølging(personIdent) else null

        val årsakTilSattPåVent = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent
        val harFortroligAdresse = enhetService.skalHaFortroligAdresse(
            oppgaveOppdatering.personIdent,
            oppgaveOppdatering.relevanteIdenter
        )

        loggOppdatering(eksisterendeOppgave)
        oppgaveRepository.oppdatereOppgave(
            oppgaveId = eksisterendeOppgave.oppgaveId(),
            ident = KELVIN,
            personIdent = personIdent,
            enhet = enhetForOppgave.enhet,
            oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            påVentTil = oppgaveOppdatering.venteInformasjon?.frist,
            påVentÅrsak = årsakTilSattPåVent,
            påVentBegrunnelse = oppgaveOppdatering.venteInformasjon?.begrunnelse,
            vurderingsbehov = oppgaveOppdatering.vurderingsbehov,
            harFortroligAdresse = harFortroligAdresse,
            harUlesteDokumenter = harUlesteDokumenter(oppgaveOppdatering),
            returInformasjon = utledReturTilToTrinn(avklaringsbehov, oppgaveOppdatering),
        )
        håndterReservasjonFraBehandlingsflyt(oppgaveOppdatering, avklaringsbehov, eksisterendeOppgave.oppgaveId())
    }

    private fun håndterReservasjonFraBehandlingsflyt(oppgaveOppdatering: OppgaveOppdatering, avklaringsbehov: AvklaringsbehovHendelse, oppgaveId: OppgaveId) {
        if (oppgaveOppdatering.reserverTil != null) {
            val avklaringsbehovReferanse = AvklaringsbehovReferanseDto(
                saksnummer = oppgaveOppdatering.saksnummer,
                referanse = oppgaveOppdatering.referanse,
                null,
                avklaringsbehov.avklaringsbehovKode.kode
            )
            reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
                avklaringsbehovReferanse,
                oppgaveOppdatering.reserverTil
            )
            log.info("Oppgave $oppgaveId automatisk reservert ${oppgaveOppdatering.reserverTil}.")
        }
    }

    private fun loggOppdatering(eksisterendeOppgave: OppgaveDto) {
        if (eksisterendeOppgave.status == Status.OPPRETTET) {
            log.info("Oppdaterer eksisterende oppgave ${eksisterendeOppgave.oppgaveId()} på avklaringsbehov ${eksisterendeOppgave.avklaringsbehovKode}. Saksnummer: ${eksisterendeOppgave.saksnummer}")
        } else {
            log.info("Gjenåpner oppgave ${eksisterendeOppgave.oppgaveId()} med tidligere status ${eksisterendeOppgave.status} på avklaringsbehov ${eksisterendeOppgave.avklaringsbehovKode}. Saksnummer: ${eksisterendeOppgave.saksnummer}")
        }
    }

    private fun utledReturTilToTrinn(
        avklaringsbehov: AvklaringsbehovHendelse,
        oppgaveOppdatering: OppgaveOppdatering,
    ): ReturInformasjon? {
        // Setter ReturInformasjon når behandling sendes tilbake til totrinn
        return if (erReturTilToTrinn(avklaringsbehov) && unleashService.isEnabled(FeatureToggles.ToTrinnForAndreGang)) {
            log.info("Totrinnsoppgave gjenåpnet, setter retur fra veileder/saksbehandler. Saksnummer: ${oppgaveOppdatering.saksnummer}")
            val forrigeAvklaringsbehovLøstAvVeileder = oppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov()?.first?.kode in AVKLARINGSBEHOV_FOR_VEILEDER.map { it.kode }
            ReturInformasjon(
                status = if (forrigeAvklaringsbehovLøstAvVeileder) {
                    ReturStatus.RETUR_FRA_VEILEDER
                } else {
                    ReturStatus.RETUR_FRA_SAKSBEHANDLER
                },
                årsaker = emptyList(),
                endretAv = oppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov()?.second ?: "Ukjent",
                begrunnelse = if (forrigeAvklaringsbehovLøstAvVeileder) {
                    "Retur fra veileder"
                } else {
                    "Retur fra saksbehandler"
                }
            )
        } else {
            null
        }
    }

    private fun avsluttOppgaverSenereIFlyt(
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        åpentAvklaringsbehov: AvklaringsbehovHendelse
    ) {
        val oppgaverSomMåAvsluttes = oppgaveMap.values.filter { it.avklaringsbehovKode != åpentAvklaringsbehov.avklaringsbehovKode.kode }
        avslutteOppgaver(oppgaverSomMåAvsluttes)
    }

    private fun opprettNyOppgaveForAvklaringsbehov(
        oppgaveOppdatering: OppgaveOppdatering,
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        åpentAvklaringsbehov: AvklaringsbehovHendelse
    ) {
        if (oppgaveMap.isNotEmpty()) {
            // Dersom det finnes åpne oppgaver fra før, skal disse avsluttes før ny oppgave opprettes.
            avslutteOppgaver(oppgaveMap.values.toList())
        }
        opprettOppgave(oppgaveOppdatering, åpentAvklaringsbehov)
    }

    private fun harBlittSendtTilbakeFraKvalitetssikrer(avklaringsbehov: AvklaringsbehovHendelse): Boolean =
        avklaringsbehov.status in setOf(
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
        )

    private fun erReturTilToTrinn(avklaringsbehov: AvklaringsbehovHendelse): Boolean {
        return (avklaringsbehov.avklaringsbehovKode.kode in setOf(
            Definisjon.KVALITETSSIKRING.kode.name,
            Definisjon.FATTE_VEDTAK.kode.name,
        ) && avklaringsbehov.endringer.any { it.status == AvklaringsbehovStatus.AVSLUTTET })
    }

    private fun gjenÅpneOppgaveEtterReturFraKvalitetssikrer(
        eksisterendeOppgave: OppgaveDto,
        avklaringsbehov: AvklaringsbehovHendelse
    ) {
        if (eksisterendeOppgave.status == Status.AVSLUTTET) {
            log.info("Gjenåpner oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}")
            oppgaveRepository.gjenåpneOppgave(
                eksisterendeOppgave.oppgaveId(), KELVIN, tilReturInformasjon(avklaringsbehov)
            )
        } else {
            log.warn("Kan ikke gjenåpne oppgave som er allerede er åpen (id=${eksisterendeOppgave.oppgaveId()}, avklaringsbehov=${avklaringsbehov.avklaringsbehovKode})")
            return
        }
        sendOppgaveStatusOppdatering(
            eksisterendeOppgave.oppgaveId(),
            HendelseType.OPPDATERT,
            flytJobbRepository
        )
        val sistEndretAv = avklaringsbehov.sistEndretAv(AvklaringsbehovStatus.AVSLUTTET)
        if (sistEndretAv != KELVIN) {
            val avklaringsbehovReferanse = eksisterendeOppgave.tilAvklaringsbehovReferanseDto()
            val oppdatertOppgave = oppgaveRepository.hentOppgave(avklaringsbehovReferanse)
            if (oppdatertOppgave != null) {
                log.info("Reserverer oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}")
                reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(avklaringsbehovReferanse, sistEndretAv)
                sendOppgaveStatusOppdatering(
                    oppdatertOppgave.oppgaveId(),
                    HendelseType.RESERVERT,
                    flytJobbRepository
                )
            } else {
                log.warn("Fant ikke oppgave som skulle reserveres: $avklaringsbehovReferanse")
            }
        } else {
            log.info("Reserverer ikke oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status} fordi $KELVIN utførte siste endring")
        }
    }

    private fun tilReturInformasjon(avklaringsbehov: AvklaringsbehovHendelse): ReturInformasjon? {
        val status = when (avklaringsbehov.status) {
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER -> ReturStatus.RETUR_FRA_BESLUTTER
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER -> ReturStatus.RETUR_FRA_KVALITETSSIKRER
            else -> null
        }
        if (status == null) return null

        val sisteEndring = avklaringsbehov.sisteEndring()

        return ReturInformasjon(
            status = status,
            årsaker = sisteEndring.årsakTilRetur.map { ÅrsakTilReturKode.valueOf(it.name) },
            begrunnelse = requireNotNull(sisteEndring.begrunnelse) { "Det skal alltid finnes begrunnelse for retur." },
            endretAv = sisteEndring.endretAv,
        )
    }

    private fun reserverOppgave(
        eksisterendeOppgave: OppgaveDto,
        venteInformasjon: VenteInformasjon
    ) {
        val avklaringsbehovReferanse = eksisterendeOppgave.tilAvklaringsbehovReferanseDto()
        val endretAv = venteInformasjon.sattPåVentAv
        reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
            avklaringsbehovReferanse,
            endretAv
        )
    }

    private fun skalOverstyresTilLokalKontor(oppgaveOppdatering: OppgaveOppdatering, avklaringsbehovHendelse: AvklaringsbehovHendelse): Boolean {
        // Hvis avklaringsbehov kan løses av begge roller, skal oppgaven gå til lokalkontor dersom forrige oppgave på behandling var på lokalkontor
        // P.t. gjelder dette trekk søknad og trekk klage
        val avklaringsbehovForBeggeRoller = AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER.map { it.kode }
        if (avklaringsbehovHendelse.avklaringsbehovKode.kode !in avklaringsbehovForBeggeRoller) {
            return false
        }
        val varForrigeOppgaveHosNavKontor = oppgaveOppdatering.avklaringsbehov
            .filter { avklaringsbehov -> avklaringsbehov.avklaringsbehovKode.kode !in avklaringsbehovForBeggeRoller }
            .maxByOrNull { it.sistEndret() }?.avklaringsbehovKode?.kode in AVKLARINGSBEHOV_FOR_VEILEDER.map { it.kode }
        return varForrigeOppgaveHosNavKontor
    }

    private fun opprettOppgave(
        oppgaveOppdatering: OppgaveOppdatering,
        avklaringsbehovHendelse: AvklaringsbehovHendelse,
    ) {
        val personIdent = oppgaveOppdatering.personIdent
        val skalOverstyresTilLokalkontor = skalOverstyresTilLokalKontor(oppgaveOppdatering, avklaringsbehovHendelse)
        val enhetForOppgave = enhetService.utledEnhetForOppgave(
            avklaringsbehovHendelse.avklaringsbehovKode,
            personIdent,
            oppgaveOppdatering.relevanteIdenter,
            oppgaveOppdatering.saksnummer,
            skalOverstyresTilLokalkontor
        )

        val veilederArbeid = if (personIdent != null) hentVeilederArbeidsoppfølging(personIdent) else null
        val veilederSykdom = if (personIdent != null) hentVeilederSykefraværoppfølging(personIdent) else null
        val harFortroligAdresse =
            enhetService.skalHaFortroligAdresse(personIdent, oppgaveOppdatering.relevanteIdenter)

        val nyOppgave = oppgaveOppdatering.opprettNyOppgave(
            personIdent = personIdent,
            avklaringsbehovKode = avklaringsbehovHendelse.avklaringsbehovKode,
            behandlingstype = oppgaveOppdatering.behandlingstype,
            ident = KELVIN,
            enhet = enhetForOppgave.enhet,
            oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            påVentTil = oppgaveOppdatering.venteInformasjon?.frist,
            påVentÅrsak = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent,
            venteBegrunnelse = oppgaveOppdatering.venteInformasjon?.begrunnelse,
            vurderingsbehov = oppgaveOppdatering.vurderingsbehov,
            årsakTilOpprettelse = oppgaveOppdatering.årsakTilOpprettelse,
            harFortroligAdresse = harFortroligAdresse,
            harUlesteDokumenter = harUlesteDokumenter(oppgaveOppdatering),
            returInformasjon = tilReturInformasjon(avklaringsbehovHendelse),
        )
        val oppgaveId = oppgaveRepository.opprettOppgave(nyOppgave)
        log.info("Ny oppgave(id=${oppgaveId.id}) ble opprettet med status ${avklaringsbehovHendelse.status} for avklaringsbehov ${avklaringsbehovHendelse.avklaringsbehovKode}. Saksnummer: ${oppgaveOppdatering.saksnummer}. Venteinformasjon: ${oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent}")
        sendOppgaveStatusOppdatering(oppgaveId, HendelseType.OPPRETTET, flytJobbRepository)

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
                reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
                    avklaringsbehovReferanse,
                    hvemLøsteForrigeIdent
                )
                log.info("Ny oppgave(id=${oppgaveId.id}) ble automatisk tilordnet: $hvemLøsteForrigeIdent. Saksnummer: ${oppgaveOppdatering.saksnummer}")

            } else {
                log.info("Ingen automatisk tilordning: Forskjellig saksbehandler-type mellom $forrigeAvklaringsbehovKode og ${nyttAvklaringsbehov.avklaringsbehovKode}")
            }
        }
        håndterReservasjonFraBehandlingsflyt(oppgaveOppdatering, avklaringsbehovHendelse, oppgaveId)
    }

    private fun hentVeilederSykefraværoppfølging(personIdent: String): String? =
        sykefravarsoppfolgingKlient.hentVeileder(personIdent)

    private fun hentVeilederArbeidsoppfølging(personIdent: String): String? =
        veilarbarboppfolgingKlient.hentVeileder(personIdent)

    private fun harUlesteDokumenter(oppgaveOppdatering: OppgaveOppdatering): Boolean {
        mottattDokumentRepository.lagreDokumenter(oppgaveOppdatering.mottattDokumenter)
        val ulesteDokumenter = mottattDokumentRepository.hentUlesteDokumenter(oppgaveOppdatering.referanse)
        return ulesteDokumenter.isNotEmpty()
    }

    private fun sammeSaksbehandlerType(
        avklaringsbehovKode1: AvklaringsbehovKode,
        avklaringsbehovKode2: AvklaringsbehovKode
    ): Boolean {
        return when (avklaringsbehovKode1) {
            in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER -> true
            in AVKLARINGSBEHOV_FOR_VEILEDER if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER -> true
            in AVKLARINGSBEHOV_FOR_BESLUTTER if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_BESLUTTER -> true
            in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK -> true
            in AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK -> true
            else -> false
        }
    }

    private fun avslutteOppgaver(oppgaver: List<OppgaveDto>) {
        oppgaver
            .filter { it.status != Status.AVSLUTTET }
            .forEach {
                oppgaveRepository.avsluttOppgave(it.oppgaveId(), KELVIN)
                log.info("Avsluttet oppgave med ID ${it.oppgaveId()}. Avklaringsbehov: ${it.avklaringsbehovKode}")
                sendOppgaveStatusOppdatering(it.oppgaveId(), HendelseType.LUKKET, flytJobbRepository)
            }
    }

    private fun OppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov(): Pair<AvklaringsbehovKode, String>? {
        val sisteAvsluttetAvklaringsbehov = avklaringsbehov
            .filter { it.status == AvklaringsbehovStatus.AVSLUTTET }
            .maxByOrNull { it.sistEndret() }

        if (sisteAvsluttetAvklaringsbehov == null) {
            val beskrivelse = avklaringsbehov.joinToString { beh ->
                val siste = beh.endringer.maxByOrNull { it.tidsstempel }
                "${beh.avklaringsbehovKode.kode}:${beh.status} (sistEndret=${siste?.tidsstempel}, av=${siste?.endretAv})"
            }
            log.info("Fant ingen avsluttede avklaringsbehov. Behovene: [$beskrivelse]")
            return null
        }

        if (sisteAvsluttetAvklaringsbehov.sistEndretAv() == KELVIN) {
            log.info("Siste avsluttede avklaringsbehov ble løst av systembruker $KELVIN, oppgave vil ikke bli reservert.")
        }

        return Pair(sisteAvsluttetAvklaringsbehov.avklaringsbehovKode, sisteAvsluttetAvklaringsbehov.sistEndretAv())
    }

    private fun AvklaringsbehovHendelse.sisteEndring(status: AvklaringsbehovStatus = this.status): Endring {
        return try {
            endringer
                .sortedBy { it.tidsstempel }
                .last { it.status == status }
        } catch (e: NoSuchElementException) {
            throw IllegalStateException(
                "Ingen endringer med status $status. Endringer: ${this.endringer}. Avklaringsbehovkode: ${this.avklaringsbehovKode}",
                e
            )
        }
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
        veilederArbeid: String?,
        veilederSykdom: String?,
        påVentTil: LocalDate?,
        påVentÅrsak: String?,
        venteBegrunnelse: String?,
        vurderingsbehov: List<String>,
        årsakTilOpprettelse: String?,
        harFortroligAdresse: Boolean,
        harUlesteDokumenter: Boolean,
        returInformasjon: ReturInformasjon?,
    ): OppgaveDto {
        return OppgaveDto(
            personIdent = personIdent,
            saksnummer = this.saksnummer,
            behandlingRef = this.referanse,
            journalpostId = this.journalpostId,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            behandlingOpprettet = this.opprettetTidspunkt,
            avklaringsbehovKode = avklaringsbehovKode.kode,
            behandlingstype = behandlingstype,
            opprettetAv = ident,
            opprettetTidspunkt = LocalDateTime.now(),
            påVentTil = påVentTil,
            påVentÅrsak = påVentÅrsak,
            venteBegrunnelse = venteBegrunnelse,
            årsakerTilBehandling = vurderingsbehov,
            vurderingsbehov = vurderingsbehov,
            årsakTilOpprettelse = årsakTilOpprettelse,
            harFortroligAdresse = harFortroligAdresse,
            returStatus = returInformasjon?.status,
            returInformasjon = returInformasjon?.let {
                ReturInformasjon(
                    status = it.status,
                    årsaker = it.årsaker,
                    begrunnelse = it.begrunnelse,
                    endretAv = it.endretAv
                )
            },
            harUlesteDokumenter = harUlesteDokumenter
        )
    }

    private fun validerOppgaveTilstandEtterOppdatering(behandlingsreferanse: UUID) {
        val åpneOppgaver = oppgaveRepository.hentOppgaver(behandlingsreferanse).filter { it.status == Status.OPPRETTET }
        if (åpneOppgaver.size > 1) {
            log.warn("Fant ${åpneOppgaver.size} åpne oppgaver for behandling $behandlingsreferanse. " +
                    "Oppgaver: ${åpneOppgaver.map { it.id }.joinToString()} " +
                    "på avklaringsbehov: ${åpneOppgaver.joinToString { it.avklaringsbehovKode }}")
        }
    }

    private fun OppgaveDto.oppgaveId() = OppgaveId(this.id!!, this.versjon)
}
