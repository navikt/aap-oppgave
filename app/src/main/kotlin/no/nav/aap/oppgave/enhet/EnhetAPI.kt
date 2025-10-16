package no.nav.aap.oppgave.enhet

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.arena.VeilarbarenaClient
import no.nav.aap.oppgave.klienter.behandlingsflyt.BehandlingsflytKlient
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.norg.NorgKlient
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.LoggerFactory
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentEnhetApi(msGraphClient: IMsGraphClient, prometheus: PrometheusMeterRegistry) =
    route("/enheter").get<Unit, List<EnhetDto>> {
        prometheus.httpCallCounter("/enheter").increment()
        val enheter = EnhetService(msGraphClient).hentEnheter(ident(), token())
        val enhetNrTilNavn = NorgKlient().hentEnheter()
        val enheterMedNavn = enheter.map { EnhetDto(it, enhetNrTilNavn[it] ?: "") }
        respond(enheterMedNavn)
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
    msGraphClient: IMsGraphClient,
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

            val behandlingRef = requireNotNull(oppgave.behandlingRef) {
                "Synkoniser oppgave: Oppgave ${oppgaveIdMedVersjon.id} mangler behandlingsreferanse"
            }
            val relaterteIdenter = BehandlingsflytKlient.hentRelevanteIdenterPåBehandling(behandlingRef)

            relaterteIdenter.forEach { VeilarbarenaClient.invalidateCache(it) }

            val nyEnhet =
                enhetService.utledEnhetForOppgave(
                    AvklaringsbehovKode(oppgave.avklaringsbehovKode),
                    oppgave.personIdent,
                    relaterteIdenter,
                    oppgave.saksnummer
                )

            val nyEnhetForKø = nyEnhet.oppfølgingsenhet ?: nyEnhet.enhet
            if (nyEnhetForKø == oppgave.enhetForKø()) {
                log.info("Ingen endring på enhet for oppgave ${oppgaveIdMedVersjon.id}, er allerede satt til $nyEnhetForKø")
            } else {
                log.info("Oppdaterer enhet for oppgave ${oppgaveIdMedVersjon.id} fra ${oppgave.enhetForKø()} til $nyEnhetForKø")
            }

            oppgaveRepository.oppdatereOppgave(
                oppgaveId = oppgaveIdMedVersjon,
                ident = "Kelvin",
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
                returInformasjon = oppgave.returInformasjon
            )

            sendOppgaveStatusOppdatering(
                oppgaveIdMedVersjon, HendelseType.OPPDATERT,
                FlytJobbRepository(connection),
            )

            EnhetSynkroniseringRespons(oppgave.enhetForKø(), nyEnhetForKø)
        }

        respond(respons)
    }

/*
 * Kode brukt for å populere enhetsfeltet på oppgave på gamle oppgaver. Lar det ligge en stund i tilfelle
 * det skal bli aktuelt å gjøre det igjen.
data class EnhetsoppdateringRapport(val antallOppgaverUtenEnhet: Int, val oppgaveOgPersonListe: List<OppgaveOgPerson>)

fun NormalOpenAPIRoute.oppdaterEnhetPåOppgaver(dataSource: DataSource, msGraphClient: IMsGraphClient) =

    route("/oppdater-enheter").get<Unit, EnhetsoppdateringRapport> {
        val log = LoggerFactory.getLogger("oppdater-enheter")

        val oppgaverUtenEnhet = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaverUtenEnhet()
        }

        val enhetService = EnhetService(msGraphClient)
        oppgaverUtenEnhet.take(100).forEach { oppgaveOgPerson ->
            try {
                dataSource.transaction { connection ->
                    val enhet = enhetService.finnEnhet(oppgaveOgPerson.personIdent)
                    OppgaveRepository(connection).oppdaterEnhet(oppgaveOgPerson.oppgaveId, enhet)
                }
            } catch (e: Exception) {
                log.warn("Fikk feil under prosessering av: $oppgaveOgPerson", e)
            }
        }

        respond(EnhetsoppdateringRapport(oppgaverUtenEnhet.size, oppgaverUtenEnhet.take(100)))
    }
 */