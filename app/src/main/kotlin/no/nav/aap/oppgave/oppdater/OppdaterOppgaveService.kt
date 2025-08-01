package no.nav.aap.oppgave.oppdater

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
import no.nav.aap.oppgave.ReturInformasjon
import no.nav.aap.oppgave.ReturStatus
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.oppfolging.ISykefravarsoppfolgingKlient
import no.nav.aap.oppgave.klienter.oppfolging.IVeilarbarboppfolgingKlient
import no.nav.aap.oppgave.klienter.oppfolging.SykefravarsoppfolgingKlient
import no.nav.aap.oppgave.klienter.oppfolging.VeilarbarboppfolgingKlient
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
    msGraphClient: IMsGraphClient,
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService(),
    private val veilarbarboppfolgingKlient: IVeilarbarboppfolgingKlient = VeilarbarboppfolgingKlient(),
    private val sykefravarsoppfolgingKlient: ISykefravarsoppfolgingKlient = SykefravarsoppfolgingKlient(),
    private val enhetService: IEnhetService = EnhetService(msGraphClient),
    private val oppgaveRepository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository,
    private val mottattDokumentRepository: MottattDokumentRepository,
) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    private val reserverOppgaveService = ReserverOppgaveService(
        oppgaveRepository,
        flytJobbRepository
    )

    fun oppdaterOppgaver(oppgaveOppdatering: OppgaveOppdatering) {
        val eksisterendeOppgaver = oppgaveRepository.hentOppgaver(referanse = oppgaveOppdatering.referanse)

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
        // TODO: rydd opp i logikk her, trekk ut metoder osv
        // Send oppdatering til statistikk på slutten, så man ikke får 3 forskjellige oppdateringer
        // å forholde seg til
        val personIdent = oppgaveOppdatering.personIdent
        val eksisterendeOppgave = oppgaveMap[avklaringsbehov.avklaringsbehovKode]
        if (eksisterendeOppgave != null) {
            val enhetForOppgave =
                enhetService.utledEnhetForOppgave(avklaringsbehov.avklaringsbehovKode, personIdent)
            val veilederArbeid = if (personIdent != null) hentVeilederArbeidsoppfølging(personIdent) else null
            val veilederSykdom = if (personIdent != null) hentVeilederSykefraværoppfølging(personIdent) else null

            if (harBlittSendtTilbakeFraKvalitetssikrer(avklaringsbehov)) {
                gjenÅpneOppgaveEtterReturFraKvalitetssikrer(eksisterendeOppgave, avklaringsbehov)
            } else {
                val årsakTilSattPåVent = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent
                val harFortroligAdresse = enhetService.harFortroligAdresse(oppgaveOppdatering.personIdent)

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
                    årsakerTilBehandling = oppgaveOppdatering.årsakerTilBehandling,
                    harFortroligAdresse = harFortroligAdresse,
                    harUlesteDokumenter = harUlesteDokumenter(oppgaveOppdatering),
                    // Setter til null for å fjerne evt tidligere status
                    returInformasjon = null,
                )
                log.info("Oppdaterer oppgave ${eksisterendeOppgave.oppgaveId()} med status ${avklaringsbehov.status}. Venteårsak: $årsakTilSattPåVent")
                sendOppgaveStatusOppdatering(
                    eksisterendeOppgave.oppgaveId(),
                    HendelseType.OPPDATERT,
                    flytJobbRepository
                )

                if (oppgaveOppdatering.venteInformasjon != null && eksisterendeOppgave.reservertAv == null) {
                    reserverOppgave(eksisterendeOppgave, oppgaveOppdatering.venteInformasjon)
                }
            }
        }
    }

    private fun harBlittSendtTilbakeFraKvalitetssikrer(avklaringsbehov: AvklaringsbehovHendelse): Boolean =
        avklaringsbehov.status in setOf(
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
        )

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
        val reserverteOppgaver =
            reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
                avklaringsbehovReferanse,
                endretAv
            )
        log.info("Oppgave ${reserverteOppgaver.joinToString(", ")} reservert $endretAv")
    }

    private fun opprettOppgaver(
        oppgaveOppdatering: OppgaveOppdatering,
        avklaringsbehovSomDetSkalOpprettesOppgaverFor: List<AvklaringsbehovHendelse>
    ) {
        avklaringsbehovSomDetSkalOpprettesOppgaverFor.forEach { avklaringsbehovHendelse ->
            val personIdent = oppgaveOppdatering.personIdent
            val enhetForOppgave = enhetService.utledEnhetForOppgave(
                avklaringsbehovHendelse.avklaringsbehovKode,
                personIdent
            )

            val veilederArbeid = if (personIdent != null) hentVeilederArbeidsoppfølging(personIdent) else null
            val veilederSykdom = if (personIdent != null) hentVeilederSykefraværoppfølging(personIdent) else null
            val harFortroligAdresse = enhetService.harFortroligAdresse(personIdent)

            val nyOppgave = oppgaveOppdatering.opprettNyOppgave(
                personIdent = personIdent,
                avklaringsbehovKode = avklaringsbehovHendelse.avklaringsbehovKode,
                behandlingstype = oppgaveOppdatering.behandlingstype,
                ident = "Kelvin",
                enhet = enhetForOppgave.enhet,
                oppfølgingsenhet = enhetForOppgave.oppfølgingsenhet,
                veilederArbeid = veilederArbeid,
                veilederSykdom = veilederSykdom,
                påVentTil = oppgaveOppdatering.venteInformasjon?.frist,
                påVentÅrsak = oppgaveOppdatering.venteInformasjon?.årsakTilSattPåVent,
                venteBegrunnelse = oppgaveOppdatering.venteInformasjon?.begrunnelse,
                årsakerTilBehandling = oppgaveOppdatering.årsakerTilBehandling,
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
                    val reserverteOppgaver = reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
                        avklaringsbehovReferanse,
                        hvemLøsteForrigeIdent
                    )
                    if (reserverteOppgaver.isNotEmpty()) {
                        log.info("Ny oppgave(id=${oppgaveId.id}) ble automatisk tilordnet: $hvemLøsteForrigeIdent. Saksnummer: ${oppgaveOppdatering.saksnummer}")
                    }
                }
            }

            if (oppgaveOppdatering.reserverTil != null) {
                val avklaringsbehovReferanse = AvklaringsbehovReferanseDto(
                    saksnummer = oppgaveOppdatering.saksnummer,
                    referanse = oppgaveOppdatering.referanse,
                    null,
                    avklaringsbehovHendelse.avklaringsbehovKode.kode
                )
                reserverOppgaveService.reserverOppgaveUtenTilgangskontroll(
                    avklaringsbehovReferanse,
                    oppgaveOppdatering.reserverTil
                )
                log.info("Oppgave ${oppgaveId.id} automatisk reservert ${oppgaveOppdatering.reserverTil}.")
            }
        }
    }

    private fun hentVeilederSykefraværoppfølging(personIdent: String): String? =
        sykefravarsoppfolgingKlient.hentVeileder(personIdent)

    private fun hentVeilederArbeidsoppfølging(personIdent: String): String? =
        veilarbarboppfolgingKlient.hentVeileder(personIdent)

    private fun harUlesteDokumenter(oppgaveOppdatering: OppgaveOppdatering): Boolean {
        // TODO legger bak feature-toggle inntil vi har avklar hva vi gjør med eksisterende prod-saker
        if (unleashService.isEnabled(FeatureToggles.LagreMottattDokumenter) && oppgaveOppdatering.mottattDokumenter.isNotEmpty()) {
            mottattDokumentRepository.lagreDokumenter(oppgaveOppdatering.mottattDokumenter)
            val ulesteDokumenter = mottattDokumentRepository.hentUlesteDokumenter(oppgaveOppdatering.referanse)
            return ulesteDokumenter.isNotEmpty()
        } else return false
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
                oppgaveRepository.avsluttOppgave(it.oppgaveId(), "Kelvin")
                log.info("AVsluttet oppgave med ID ${it.oppgaveId()}.")
                sendOppgaveStatusOppdatering(it.oppgaveId(), HendelseType.LUKKET, flytJobbRepository)
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
        veilederArbeid: String?,
        veilederSykdom: String?,
        påVentTil: LocalDate?,
        påVentÅrsak: String?,
        venteBegrunnelse: String?,
        årsakerTilBehandling: List<String>,
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
            årsakerTilBehandling = årsakerTilBehandling,
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

    private fun OppgaveDto.oppgaveId() = OppgaveId(this.id!!, this.versjon)
}
