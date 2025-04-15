package no.nav.aap.oppgave.plukk

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.tilgang.Beslutter
import no.nav.aap.tilgang.Kvalitetssikrer
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.SaksbehandlerNasjonal
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import no.nav.aap.tilgang.authorizedPost
import javax.sql.DataSource

fun NormalOpenAPIRoute.plukkNesteApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =
    // Trenger ikke ytterligere tilgangskontroll da tilgang kalles for å finne plukkbar oppgave
    route("/neste-oppgave").authorizedPost<Unit, NesteOppgaveDto, FinnNesteOppgaveDto>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/neste-oppgave").increment()
        val nesteOppgave = dataSource.transaction { connection ->
            PlukkOppgaveService(connection, prometheus).plukkNesteOppgave(request.filterId, request.enheter, ident(), token())
        }
        if (nesteOppgave != null) {
            respond(nesteOppgave)
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }

fun NormalOpenAPIRoute.plukkOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =
    // Trenger ikke ytterligere tilgangskontroll da tilgang kalles ved plukking
    route("/plukk-oppgave").authorizedPost<Unit, OppgaveDto, PlukkOppgaveDto>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/plukk-oppgave").increment()
        val oppgave = dataSource.transaction { connection ->
            PlukkOppgaveService(connection, prometheus).plukkOppgave(
                OppgaveId(request.oppgaveId, request.versjon),
                ident(),
                token()
            )
        }
        if (oppgave != null) {
            respond(oppgave)
        } else {
            respondWithStatus(HttpStatusCode.Unauthorized)
        }
    }