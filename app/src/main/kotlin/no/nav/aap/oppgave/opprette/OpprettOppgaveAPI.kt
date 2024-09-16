package no.nav.aap.oppgave.opprette

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.verdityper.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.metriker.httpCallCounter
import no.nav.aap.oppgave.opprett.BehandlingshistorikkRequest
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource

fun NormalOpenAPIRoute.opprettOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/opprett-oppgave").post<Unit, OppgaveId, BehandlingshistorikkRequest> { _, request ->
        prometheus.httpCallCounter("/opprett-oppgave").increment()
        val oppgave = BehandlingshistorikkTilOppgaveConverter.lagOppgave(request, ident())
        if (oppgave != null) {
            val oppgaveId =  dataSource.transaction { connection ->
                OppgaveRepository(connection).opprettOppgave(oppgave)
            }
            respond(oppgaveId)
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }
