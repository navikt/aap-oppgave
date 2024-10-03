package no.nav.aap.oppgave.plukk

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource

fun NormalOpenAPIRoute.plukkApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/neste-oppgave").post<Unit, NesteOppgaveDto, FinnNesteOppgaveDto> { _, request ->
        prometheus.httpCallCounter("/neste-oppgave").increment()
        val nesteOppgave =  dataSource.transaction { connection ->
            PlukkOppgaveService(connection).plukkNesteOppgave(request.filterId, ident(), token())
        }
        if (nesteOppgave != null) {
            respond(nesteOppgave)
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }
