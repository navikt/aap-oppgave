package no.nav.aap.oppgave.oppdater

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import javax.sql.DataSource

fun NormalOpenAPIRoute.oppdaterBehandlingOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/oppdater-oppgaver").post<Unit, Unit, BehandlingFlytStoppetHendelse> { _, request ->
        prometheus.httpCallCounter("/oppdater-oppgaver").increment()
        dataSource.transaction { connection ->
            OppdaterOppgaveService(connection).oppdaterOppgaver(request.tilOppgaveOppdatering())
        }
        respondWithStatus(HttpStatusCode.OK)
    }

fun NormalOpenAPIRoute.oppdaterPostmottakOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/oppdater-postmottak-oppgaver").post<Unit, Unit, DokumentflytStoppetHendelse> { _, request ->
        prometheus.httpCallCounter("/oppdater-postmottak-oppgaver").increment()
        dataSource.transaction { connection ->
            OppdaterOppgaveService(connection).oppdaterOppgaver(request.tilOppgaveOppdatering())
        }
        respondWithStatus(HttpStatusCode.OK)
    }
