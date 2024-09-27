package no.nav.aap.oppgave.opprette

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.metriker.httpCallCounter
import javax.sql.DataSource

fun NormalOpenAPIRoute.opprettOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/opprett-oppgave").post<Unit, Unit, BehandlingFlytStoppetHendelse> { _, request ->
        prometheus.httpCallCounter("/opprett-oppgave").increment()
        dataSource.transaction { connection ->
            OppdaterOppgaveService(connection).oppdaterOppgaver(request, token())
        }
        respondWithStatus(HttpStatusCode.OK)
    }
