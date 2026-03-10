package no.nav.aap.oppgave.enhet

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.arena.VeilarbarenaGateway
import no.nav.aap.oppgave.klienter.behandlingsflyt.BehandlingsflytGateway
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.klienter.norg.NorgGateway
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppdater.hendelse.KELVIN
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.tilgang.AuthorizationMachineToMachineConfig
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.authorizedPost
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentEnhetApi(msGraphClient: IMsGraphGateway, prometheus: PrometheusMeterRegistry) =
    route("/enheter").get<Unit, List<EnhetDto>> {
        prometheus.httpCallCounter("/enheter").increment()
        val enheter = EnhetService(msGraphClient).hentEnheter(ident(), token())
        val enhetNrTilNavn = NorgGateway().hentEnheter()
        val enheterMedNavn = enheter.map { EnhetDto(it, enhetNrTilNavn[it] ?: "") }
        respond(enheterMedNavn)
    }

fun NormalOpenAPIRoute.nayEnhetForPerson(msGraphClient: IMsGraphGateway, prometheus: PrometheusMeterRegistry) =
    route("/enhet/nay/person").post<Unit, EnhetNrDto, EnhetForPersonRequest> { _, request ->
        prometheus.httpCallCounter("/enhet/nay/person").increment()
        val enhet = EnhetService(msGraphClient).finnNayEnhet(request.personIdent, request.relevanteIdenter)
        respond(EnhetNrDto(enhetNr = enhet.enhet))
    }

/**
 * Ment for NKS via api-intern. Skal returnere hvilken enhet som behandler en sak for en person
 * på tidspunktet spørringen skjer. Hvis det ikke finnes noen åpne oppgaver, returneres null.
 *
 * Gir kun svar hvis det finnes en åpen behandling. Så hvis eneste oppgave er journalføring
 * vil vi returnere null.
 */
fun NormalOpenAPIRoute.enhetStatus(dataSource: DataSource) =
    route("/enhet/status/person").authorizedPost<Unit, EnhetOgOversendelse, PersonRequest>(
        routeConfig = AuthorizationMachineToMachineConfig(
            authorizedAzps = listOf(UUID.fromString(requiredConfigForKey("AZP_API_INTERN")))
        )
    ) { _, request ->
        val log = LoggerFactory.getLogger("enhet-status")

        val respons = dataSource.transaction { connection ->
            val oppgaver = OppgaveRepository(connection)
                .hentOppgaverForIdent(request.ident)
                // Filtrer kun oppgaver med behandlingsreferanse
                .filter { it.behandlingstype.fraBehandlingsflyt }
                .sortedBy { it.opprettetTidspunkt }

            val erHosNAY: (oppgave: OppgaveDto) -> Boolean =
                { oppgave -> oppgave.enhetForKø in NAY_ENHETER.map { it.kode } }

            val erBeslutterOppgave: (oppgave: OppgaveDto) -> Boolean =
                { oppgave -> Rolle.BESLUTTER in Definisjon.forKode(oppgave.avklaringsbehovKode).løsesAv }

            val lokalkontoroppgaver =
                oppgaver.filter { oppgave ->
                    !erHosNAY(oppgave)
                }

            val medlemskap =
                oppgaver.firstOrNull { oppgave ->
                    erHosNAY(oppgave)
                            && oppgave.avklaringsbehovKode == Definisjon.AVKLAR_LOVVALG_MEDLEMSKAP.kode.name
                }

            val kvalitetssikrer =
                oppgaver.filter { oppgave ->
                    oppgave.avklaringsbehovKode == Definisjon.KVALITETSSIKRING.kode.name
                }

            val oversendtTilNay =
                oppgaver.filter { oppgave ->
                    erHosNAY(oppgave)
                            && !erBeslutterOppgave(oppgave)
                            && oppgave.avklaringsbehovKode != Definisjon.AVKLAR_LOVVALG_MEDLEMSKAP.kode.name
                }

            val beslutter = oppgaver.filter { oppgave ->
                erHosNAY(oppgave)
                        && erBeslutterOppgave(oppgave)
            }

            return@transaction when {
                // Eneste avklaringsbehov hos NAY som er før lokalkontor
                medlemskap != null && medlemskap.erÅpen -> NåværendeEnhet(
                    oversendtDato = medlemskap.opprettetTidspunkt.toLocalDate(),
                    oppgaveKategori = OppgaveKategori.MEDLEMSKAP,
                    enhet = medlemskap.enhetForKø,
                    saksnummer = requireNotNull(medlemskap.saksnummer)
                )

                lokalkontoroppgaver.isNotEmpty() && lokalkontoroppgaver.any { it.erÅpen } -> {
                    val førsteOppgave = lokalkontoroppgaver.first()
                    NåværendeEnhet(
                        oversendtDato = førsteOppgave.opprettetTidspunkt.toLocalDate(),
                        oppgaveKategori = OppgaveKategori.LOKALKONTOR,
                        enhet = førsteOppgave.enhetForKø,
                        saksnummer = requireNotNull(førsteOppgave.saksnummer)
                    )
                }

                kvalitetssikrer.isNotEmpty() && kvalitetssikrer.any { it.erÅpen } -> {
                    val førsteOppgave = kvalitetssikrer.first()
                    NåværendeEnhet(
                        oversendtDato = førsteOppgave.opprettetTidspunkt.toLocalDate(),
                        oppgaveKategori = OppgaveKategori.KVALITETSSIKRING,
                        enhet = førsteOppgave.enhetForKø,
                        saksnummer = requireNotNull(førsteOppgave.saksnummer)
                    )
                }

                oversendtTilNay.isNotEmpty() && oversendtTilNay.any { it.erÅpen } -> {
                    val førsteOppgave = oversendtTilNay.first()
                    NåværendeEnhet(
                        oversendtDato = førsteOppgave.opprettetTidspunkt.toLocalDate(),
                        oppgaveKategori = OppgaveKategori.NAY,
                        enhet = førsteOppgave.enhetForKø,
                        saksnummer = requireNotNull(førsteOppgave.saksnummer)
                    )
                }

                beslutter.isNotEmpty() && beslutter.any { it.erÅpen } -> {
                    val førsteOppgave = beslutter.first()
                    NåværendeEnhet(
                        oversendtDato = førsteOppgave.opprettetTidspunkt.toLocalDate(),
                        oppgaveKategori = OppgaveKategori.BESLUTTER,
                        enhet = førsteOppgave.enhetForKø,
                        saksnummer = requireNotNull(førsteOppgave.saksnummer)
                    )
                }

                oppgaver.isNotEmpty() && oppgaver.none { it.erÅpen } && lokalkontoroppgaver.isNotEmpty() -> {
                    log.info("Ingen åpne oppgaver. Velger enhet for siste åpne lokalkontoroppgave.")
                    val sistÅpnedeOppgave = lokalkontoroppgaver.last()

                    NåværendeEnhet(
                        oversendtDato = sistÅpnedeOppgave.opprettetTidspunkt.toLocalDate(),
                        oppgaveKategori = OppgaveKategori.LOKALKONTOR,
                        enhet = sistÅpnedeOppgave.enhetForKø,
                        saksnummer = requireNotNull(sistÅpnedeOppgave.saksnummer)
                    )
                }

                oppgaver.isNotEmpty() -> {
                    log.info("Uventet kategori. Velger enhet for siste åpne oppgave.")
                    val sistÅpnedeOppgave = oppgaver.last()
                    NåværendeEnhet(
                        oversendtDato = sistÅpnedeOppgave.opprettetTidspunkt.toLocalDate(),
                        oppgaveKategori = if (erHosNAY(sistÅpnedeOppgave)) OppgaveKategori.NAY else OppgaveKategori.LOKALKONTOR,
                        enhet = sistÅpnedeOppgave.enhetForKø,
                        saksnummer = requireNotNull(sistÅpnedeOppgave.saksnummer)
                    )
                }

                else -> {
                    log.info("Fant ingen åpne oppgaver. Returnerer null")
                    null
                }
            }
        }

        respond(EnhetOgOversendelse(respons))
    }

/*
 * Synkroniserer enhet på oppgaven etter at oppfølgingsenhet er endret i Arena,
 * midlertidig løsning inntil permanenent løsning for oppfølgingskontor post Arena er på plass
 */
data class EnhetSynkroniseringRequest(
    val oppgaveId: Long
)

data class EnhetSynkroniseringRespons(
    val gammelEnhet: String,
    val nyEnhet: String,
)

fun NormalOpenAPIRoute.synkroniserEnhetPåOppgaveApi(
    dataSource: DataSource,
    msGraphClient: IMsGraphGateway,
    prometheus: PrometheusMeterRegistry
) =
    route("/synkroniser-enhet-paa-oppgave").post<Unit, EnhetSynkroniseringRespons, EnhetSynkroniseringRequest> { _, request ->
        prometheus.httpCallCounter("/synkroniser-enhet-paa-oppgave").increment()
        val log = LoggerFactory.getLogger("synkroniser-enhet-paa-oppgave")

        log.info("Synkoniserer oppgave for ${request.oppgaveId} ")
        val respons = dataSource.transaction { connection ->
            val enhetService = EnhetService(msGraphClient)
            val oppgaveRepository = OppgaveRepository(connection)

            val oppgave = oppgaveRepository.hentOppgave(request.oppgaveId)
            val oppgaveIdMedVersjon = OppgaveId(oppgave.id!!, oppgave.versjon)

            val behandlingRef = oppgave.behandlingRef
            val relaterteIdenter = BehandlingsflytGateway.hentRelevanteIdenterPåBehandling(behandlingRef)

            relaterteIdenter.forEach { VeilarbarenaGateway.invalidateCache(it) }

            // må sjekke om oppgaven tidligere er overstyrt til lokalkontor. Da skal den bli det igjen.
            val erOverstyrtTilLokalkontor =
                oppgave.avklaringsbehovKode in AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER.map { it.kode } && oppgave.enhet !in NAY_ENHETER.map { it.kode }
            val erFørstegangsbehandling = oppgave.behandlingstype == Behandlingstype.FØRSTEGANGSBEHANDLING

            val nyEnhet =
                enhetService.utledEnhetForOppgave(
                    AvklaringsbehovKode(oppgave.avklaringsbehovKode),
                    oppgave.personIdent,
                    relaterteIdenter,
                    oppgave.saksnummer,
                    erOverstyrtTilLokalkontor,
                    erFørstegangsbehandling
                )

            val nyEnhetForKø = nyEnhet.oppfølgingsenhet ?: nyEnhet.enhet
            if (nyEnhetForKø == oppgave.enhetForKø) {
                log.info("Ingen endring på enhet for oppgave ${oppgaveIdMedVersjon.id}, er allerede satt til $nyEnhetForKø")
            } else {
                log.info("Oppdaterer enhet for oppgave ${oppgaveIdMedVersjon.id} fra ${oppgave.enhetForKø} til $nyEnhetForKø")
            }

            oppgaveRepository.oppdatereOppgave(
                oppgaveId = oppgaveIdMedVersjon,
                endretAvIdent = KELVIN,
                personIdent = oppgave.personIdent,
                enhet = nyEnhet.enhet,
                påVentTil = oppgave.påVentTil,
                påVentÅrsak = oppgave.påVentÅrsak,
                påVentBegrunnelse = oppgave.venteBegrunnelse,
                oppfølgingsenhet = nyEnhet.oppfølgingsenhet,
                veilederArbeid = oppgave.veilederArbeid,
                veilederSykdom = oppgave.veilederSykdom,
                vurderingsbehov = oppgave.vurderingsbehov,
                harFortroligAdresse = oppgave.harFortroligAdresse,
                erSkjermet = oppgave.erSkjermet == true,
                returInformasjon = oppgave.returInformasjon,
                utløptVentefrist = oppgave.utløptVentefrist
            )

            sendOppgaveStatusOppdatering(
                oppgaveIdMedVersjon, HendelseType.OPPDATERT,
                FlytJobbRepository(connection),
            )

            EnhetSynkroniseringRespons(oppgave.enhetForKø, nyEnhetForKø)
        }

        respond(respons)
    }