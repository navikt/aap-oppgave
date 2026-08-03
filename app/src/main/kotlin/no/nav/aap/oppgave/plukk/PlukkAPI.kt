package no.nav.aap.oppgave.plukk

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.exception.ApiException
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoGateway
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.tilgang.Beslutter
import no.nav.aap.tilgang.Kvalitetssikrer
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.SaksbehandlerNasjonal
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import no.nav.aap.tilgang.authorizedPost
import javax.sql.DataSource
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.Plukket
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.Avsluttet
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.IngenTilgang
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.AlleredeTildelt
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("plukkApi")

fun NormalOpenAPIRoute.plukkOppgaveApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry,
    enhetService: EnhetService,
    ansattInfoGateway: AnsattInfoGateway,
) {
    route("/plukk-oppgave").authorizedPost<Unit, PlukkOppgaveResponse, PlukkOppgaveRequest>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/plukk-oppgave").increment()
        when (
            val plukketOppgave = PlukkOppgaveService.plukkOppgave(
                dataSource = dataSource,
                enhetService = enhetService,
                ansattInfoGateway = ansattInfoGateway,
                token = token(),
                ident = ident(),
                oppgaveId = request.oppgaveId,
                versjon = request.versjon,
            )
        ) {
            is Plukket ->  respond(
                PlukkOppgaveResponse(
                    BehandlingskontekstResponse(
                        behandlingsreferanse = plukketOppgave.oppgave.behandlingRef,
                        saksnummer = plukketOppgave.oppgave.saksnummer,
                        journalpostId = plukketOppgave.oppgave.journalpostId,
                        behandlingstype = plukketOppgave.oppgave.behandlingstype,
                        tilbakekrevingUrl = plukketOppgave.oppgave.tilbakekrevingsVars?.tilbakekrevings_URL
                    )
                )
            )
            IngenTilgang -> {
                log.info("Bruker kunne ikke plukke oppgave grunnet manglende tilgang")
                respondWithStatus(HttpStatusCode.Unauthorized)
            }

            AlleredeTildelt -> throw ApiException(
                status = HttpStatusCode.Conflict,
                message = "Oppgaven er allerede tildelt."
            )

            Avsluttet -> throw ApiException(
                status = HttpStatusCode.Conflict,
                message = "Oppgaven er avsluttet."
            )
        }
    }
}