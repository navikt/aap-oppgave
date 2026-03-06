package no.nav.aap.oppgave.oppdater

import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.klienter.oppfolging.ISykefravarsoppfolgingGateway
import no.nav.aap.oppgave.klienter.oppfolging.IVeilarbarboppfolgingGateway
import no.nav.aap.oppgave.klienter.oppfolging.SykefravarsoppfolgingGateway
import no.nav.aap.oppgave.klienter.oppfolging.VeilarbarboppfolgingGateway
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovHendelse
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovStatus
import no.nav.aap.oppgave.oppdater.hendelse.BehandlingStatus
import no.nav.aap.oppgave.oppdater.hendelse.KELVIN
import no.nav.aap.oppgave.oppdater.hendelse.OppgaveOppdatering
import no.nav.aap.oppgave.oppdater.hendelse.VenteInformasjon
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.tilbakekreving.TilbakekrevingRepository
import no.nav.aap.oppgave.tilbakekreving.TilbakekrevingVars
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.slf4j.LoggerFactory
import java.util.*

class OppdaterOppgaveService(
    msGraphClient: IMsGraphGateway,
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService(),
    private val veilarbarboppfolgingKlient: IVeilarbarboppfolgingGateway = VeilarbarboppfolgingGateway,
    private val sykefravarsoppfolgingKlient: ISykefravarsoppfolgingGateway = SykefravarsoppfolgingGateway,
    private val enhetService: IEnhetService = EnhetService(msGraphClient),
    private val oppgaveRepository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository,
    private val tilbakekrevingRepository: TilbakekrevingRepository,
    private val mottattDokumentRepository: MottattDokumentRepository,
) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    private val reserverOppgaveService = ReserverOppgaveService(
        oppgaveRepository,
        flytJobbRepository,
    )

    private val returInformasjonUtleder = ReturInformasjonUtleder()

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
        val åpentAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.firstOrNull { it.status.erÅpent() }
        val avsluttedeAvklaringsbehov = oppgaveOppdatering.avklaringsbehov.filter { it.status.erAvsluttet() }

        if (åpentAvklaringsbehov != null) {
            val eksisterendeOppgave = oppgaveMap[åpentAvklaringsbehov.avklaringsbehovKode]
            if (eksisterendeOppgave == null) {
                opprettNyOppgaveForAvklaringsbehov(oppgaveOppdatering, oppgaveMap, åpentAvklaringsbehov)
            } else {
                avsluttOppgaverSenereIFlyt(oppgaveMap, åpentAvklaringsbehov)
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
        oppdaterOppgave(oppgaveOppdatering, avklaringsbehov, eksisterendeOppgave)
        sendOppgaveStatusOppdatering(
            eksisterendeOppgave.oppgaveId(),
            HendelseType.OPPDATERT,
            flytJobbRepository
        )

        // Hvis oppgaven ble satt på vent, reserver til saksbehandler som satte på vent
        if (oppgaveOppdatering.venteInformasjon != null && eksisterendeOppgave.påVentTil == null && eksisterendeOppgave.reservertAv == null) {
            log.info("Forsøker å reservere oppgave ${eksisterendeOppgave.oppgaveId()} til saksbehandler som satte den på vent")
            reserverOppgave(oppgaveOppdatering.referanse, oppgaveOppdatering.venteInformasjon)
        }
    }

    private fun oppdaterOppgave(
        oppgaveOppdatering: OppgaveOppdatering,
        avklaringsbehov: AvklaringsbehovHendelse,
        eksisterendeOppgave: OppgaveDto
    ) {
        val personIdent = oppgaveOppdatering.personIdent
        val skalOverstyresTilLokalkontor = skalOverstyresTilLokalKontor(oppgaveOppdatering, avklaringsbehov)
        val erFørstegangsbehandling = eksisterendeOppgave.behandlingstype == Behandlingstype.FØRSTEGANGSBEHANDLING
        val enhetForOppgave =
            enhetService.utledEnhetForOppgave(
                avklaringsbehov.avklaringsbehovKode,
                personIdent,
                oppgaveOppdatering.relevanteIdenter,
                oppgaveOppdatering.saksnummer,
                skalOverstyresTilLokalkontor,
                erFørstegangsbehandling
            )
        val årsakTilSattPåVent = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent
        val harFortroligAdresse = enhetService.skalHaFortroligAdresse(
            oppgaveOppdatering.personIdent,
            oppgaveOppdatering.relevanteIdenter
        )
        val erSkjermet = enhetService.erSkjermet(oppgaveOppdatering.personIdent)

        loggOppdatering(eksisterendeOppgave, oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent)
        oppgaveRepository.oppdatereOppgave(
            oppgaveId = eksisterendeOppgave.oppgaveId(),
            endretAvIdent = KELVIN,
            personIdent = personIdent,
            enhet = enhetForOppgave.enhet,
            oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
            veilederArbeid = hentVeilederArbeidsoppfølging(personIdent),
            veilederSykdom = hentVeilederSykefraværoppfølging(personIdent),
            påVentTil = oppgaveOppdatering.venteInformasjon?.frist,
            påVentÅrsak = årsakTilSattPåVent,
            påVentBegrunnelse = oppgaveOppdatering.venteInformasjon?.begrunnelse,
            vurderingsbehov = oppgaveOppdatering.vurderingsbehov,
            harFortroligAdresse = harFortroligAdresse,
            erSkjermet = erSkjermet,
            harUlesteDokumenter = harUlesteDokumenter(oppgaveOppdatering),
            returInformasjon = returInformasjonUtleder.utledReturInformasjon(avklaringsbehov, oppgaveOppdatering),
            utløptVentefrist = utledUtløptVentefrist(oppgaveOppdatering, eksisterendeOppgave)
        )

        if (oppgaveOppdatering.behandlingstype == Behandlingstype.TILBAKEKREVING && oppgaveOppdatering.totaltFeilutbetaltBeløp != null && oppgaveOppdatering.tilbakekrevingsUrl != null) {
            tilbakekrevingRepository.lagre(
                TilbakekrevingVars(
                    eksisterendeOppgave.oppgaveId().id,
                    oppgaveOppdatering.totaltFeilutbetaltBeløp,
                    oppgaveOppdatering.tilbakekrevingsUrl
                )
            )
        }

        // Automatisk reservasjon enten ved retur eller override fra behandlingsflyt
        if (avklaringsbehov.status.harBlittSendtTilbakeFraTotrinn() && eksisterendeOppgave.status == Status.AVSLUTTET) {
            utledReservasjonEtterReturFraTotrinn(avklaringsbehov, eksisterendeOppgave)
        } else {
            // reservasjon fra behandlingsflyt skal kun overstyre når oppgave opprettes eller gjenåpnes, ikke når oppgave oppdateres
            if (eksisterendeOppgave.status == Status.AVSLUTTET) {
                håndterReservasjonFraBehandlingsflyt(
                    oppgaveOppdatering,
                    eksisterendeOppgave.oppgaveId()
                )
            }
        }
    }

    private fun håndterReservasjonFraBehandlingsflyt(
        oppgaveOppdatering: OppgaveOppdatering,
        oppgaveId: OppgaveId
    ) {
        if (oppgaveOppdatering.reserverTil != null) {
            reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
                oppgaveOppdatering.referanse,
                oppgaveOppdatering.reserverTil
            )
            log.info("Oppgave $oppgaveId automatisk reservert ${oppgaveOppdatering.reserverTil}.")
        }
    }

    private fun loggOppdatering(eksisterendeOppgave: OppgaveDto, årsakTilSattPåVent: String?) {
        if (eksisterendeOppgave.erÅpen) {
            log.info("Oppdaterer eksisterende oppgave ${eksisterendeOppgave.oppgaveId()} på avklaringsbehov ${eksisterendeOppgave.avklaringsbehovKode}. Saksnummer: ${eksisterendeOppgave.saksnummer}. Venteinformasjon: $årsakTilSattPåVent")
        } else {
            log.info("Gjenåpner oppgave ${eksisterendeOppgave.oppgaveId()} med tidligere status ${eksisterendeOppgave.status} på avklaringsbehov ${eksisterendeOppgave.avklaringsbehovKode}. Saksnummer: ${eksisterendeOppgave.saksnummer}")
        }
    }


    private fun avsluttOppgaverSenereIFlyt(
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        åpentAvklaringsbehov: AvklaringsbehovHendelse
    ) {
        val oppgaverSomMåAvsluttes =
            oppgaveMap.values.filter { it.avklaringsbehovKode != åpentAvklaringsbehov.avklaringsbehovKode.kode }
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

    private fun utledReservasjonEtterReturFraTotrinn(
        avklaringsbehov: AvklaringsbehovHendelse,
        eksisterendeOppgave: OppgaveDto
    ) {
        val sistEndretAv = avklaringsbehov.sistEndretAv(AvklaringsbehovStatus.AVSLUTTET)
        if (sistEndretAv != KELVIN) {
            val oppdatertOppgave =
                oppgaveRepository.hentOppgave(requireNotNull(eksisterendeOppgave.id) { "Eksisterende oppgave-id kan ikke være null" })

            log.info("Reserverer oppgave ${oppdatertOppgave.oppgaveId()} med status ${avklaringsbehov.status}")
            reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(eksisterendeOppgave.behandlingRef, sistEndretAv)
            sendOppgaveStatusOppdatering(
                oppdatertOppgave.oppgaveId(),
                HendelseType.RESERVERT,
                flytJobbRepository
            )

        } else {
            log.info("Reserverer ikke oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status} fordi $KELVIN utførte siste endring")
        }
    }

    private fun reserverOppgave(
        behandlingsReferanse: UUID,
        venteInformasjon: VenteInformasjon
    ) {
        val endretAv = venteInformasjon.sattPåVentAv
        reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
            behandlingsReferanse,
            endretAv
        )
    }

    private fun opprettOppgave(
        oppgaveOppdatering: OppgaveOppdatering,
        avklaringsbehovHendelse: AvklaringsbehovHendelse,
    ) {
        val personIdent = oppgaveOppdatering.personIdent
        val skalOverstyresTilLokalkontor = skalOverstyresTilLokalKontor(oppgaveOppdatering, avklaringsbehovHendelse)
        val erFørstegangsbehandling = oppgaveOppdatering.behandlingstype == Behandlingstype.FØRSTEGANGSBEHANDLING
        val enhetForOppgave = enhetService.utledEnhetForOppgave(
            avklaringsbehovHendelse.avklaringsbehovKode,
            personIdent,
            oppgaveOppdatering.relevanteIdenter,
            oppgaveOppdatering.saksnummer,
            skalOverstyresTilLokalkontor,
            erFørstegangsbehandling
        )
        val harFortroligAdresse =
            enhetService.skalHaFortroligAdresse(personIdent, oppgaveOppdatering.relevanteIdenter)
        val erSkjermet = enhetService.erSkjermet(oppgaveOppdatering.personIdent)

        val nyOppgave = oppgaveOppdatering.opprettNyOppgave(
            personIdent = personIdent,
            avklaringsbehovKode = avklaringsbehovHendelse.avklaringsbehovKode,
            behandlingstype = oppgaveOppdatering.behandlingstype,
            ident = KELVIN,
            enhet = enhetForOppgave.enhet,
            oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
            veilederArbeid = hentVeilederArbeidsoppfølging(personIdent),
            veilederSykdom = hentVeilederSykefraværoppfølging(personIdent),
            påVentTil = oppgaveOppdatering.venteInformasjon?.frist,
            påVentÅrsak = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent,
            venteBegrunnelse = oppgaveOppdatering.venteInformasjon?.begrunnelse,
            vurderingsbehov = oppgaveOppdatering.vurderingsbehov,
            årsakTilOpprettelse = oppgaveOppdatering.årsakTilOpprettelse,
            harFortroligAdresse = harFortroligAdresse,
            erSkjermet = erSkjermet,
            harUlesteDokumenter = harUlesteDokumenter(oppgaveOppdatering),
            returInformasjon = returInformasjonUtleder.utledReturInformasjon(avklaringsbehovHendelse, oppgaveOppdatering),
            saksnummer = oppgaveOppdatering.saksnummer ?: utledSaksnummerFraIdent(oppgaveOppdatering.personIdent),
        )
        val oppgaveId = oppgaveRepository.opprettOppgave(nyOppgave)
        if (oppgaveOppdatering.behandlingstype == Behandlingstype.TILBAKEKREVING && oppgaveOppdatering.totaltFeilutbetaltBeløp != null && oppgaveOppdatering.tilbakekrevingsUrl != null) {
            tilbakekrevingRepository.lagre(
                TilbakekrevingVars(
                    oppgaveId.id, oppgaveOppdatering.totaltFeilutbetaltBeløp, oppgaveOppdatering.tilbakekrevingsUrl
                )
            )
        }
        log.info("Ny oppgave(id=${oppgaveId.id}) ble opprettet med status ${avklaringsbehovHendelse.status} for avklaringsbehov ${avklaringsbehovHendelse.avklaringsbehovKode}. Saksnummer: ${oppgaveOppdatering.saksnummer}")
        sendOppgaveStatusOppdatering(oppgaveId, HendelseType.OPPRETTET, flytJobbRepository)

        val hvemLøsteForrigeAvklaringsbehov = oppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov()
        if (hvemLøsteForrigeAvklaringsbehov != null) {
            val (forrigeAvklaringsbehovKode, hvemLøsteForrigeIdent) = hvemLøsteForrigeAvklaringsbehov
            if (sammeSaksbehandlerType(forrigeAvklaringsbehovKode, avklaringsbehovHendelse.avklaringsbehovKode)) {
                log.info("Prøver å tilordne ny oppgave(id=${oppgaveId.id}) automatisk til: $hvemLøsteForrigeIdent. Saksnummer: ${oppgaveOppdatering.saksnummer}")
                reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
                    oppgaveOppdatering.referanse,
                    hvemLøsteForrigeIdent
                )


            } else {
                log.info("Ingen automatisk tilordning: Forskjellig saksbehandler-type mellom $forrigeAvklaringsbehovKode og ${avklaringsbehovHendelse.avklaringsbehovKode}")
            }
        }
        håndterReservasjonFraBehandlingsflyt(oppgaveOppdatering, oppgaveId)
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

    private fun utledSaksnummerFraIdent(ident: String): String? {
        // gitt at postmottak ikke sender saksnummer, utled fra behandlingsflyt-oppgaver om de finnes
        val oppgaverPåBruker = oppgaveRepository.hentOppgaverForIdent(ident)
        return oppgaverPåBruker.map { it.saksnummer }.firstOrNull()
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

    private fun validerOppgaveTilstandEtterOppdatering(behandlingsreferanse: UUID) {
        val åpneOppgaver = oppgaveRepository.hentOppgaver(behandlingsreferanse).filter { it.status == Status.OPPRETTET }
        if (åpneOppgaver.size > 1) {
            log.warn(
                "Fant ${åpneOppgaver.size} åpne oppgaver for behandling $behandlingsreferanse. " +
                        "Oppgaver: ${åpneOppgaver.map { it.id }.joinToString()} " +
                        "på avklaringsbehov: ${åpneOppgaver.joinToString { it.avklaringsbehovKode }}"
            )
        }
    }
}
