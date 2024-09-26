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
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("OpprettOppgaveAPI")

fun NormalOpenAPIRoute.opprettOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/opprett-oppgave").post<Unit, Unit, BehandlingFlytStoppetHendelse> { _, request ->
        prometheus.httpCallCounter("/opprett-oppgave").increment()
        dataSource.transaction { connection ->
            val avklaringsbehovReferanse = OppdaterOppgaveService(connection).oppdaterOppgaver(request)
            if (avklaringsbehovReferanse != null) {
                val hvemLøsteForrigeAvklaringsbehovIdent = request.hvemLøsteForrigeAvklaringsbehov()
                if (hvemLøsteForrigeAvklaringsbehovIdent != null) {
                    val reserverteOppgaver = ReserverOppgaveService(connection).reserverOppgave(avklaringsbehovReferanse, hvemLøsteForrigeAvklaringsbehovIdent, token())
                    if (reserverteOppgaver.isNotEmpty()) {
                        log.info("Ny oppgave ble automatisk tilordnet: $hvemLøsteForrigeAvklaringsbehovIdent")
                    }
                }
            }
        }
        respondWithStatus(HttpStatusCode.OK)
    }
