package no.nav.aap.oppgave.opprette

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.verdityper.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.metriker.httpCallCounter
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("OpprettOppgaveAPI")

fun NormalOpenAPIRoute.opprettOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/opprett-oppgave").post<Unit, OppgaveId, BehandlingFlytStoppetHendelse> { _, request ->
        prometheus.httpCallCounter("/opprett-oppgave").increment()
        val oppgave = request.lagOppgave(ident())
        if (oppgave != null) {
            val oppgaveId =  dataSource.transaction { connection ->
                val oppgaveId = OppgaveRepository(connection).opprettOppgave(oppgave)
                val hvemLøsteForrigeAvklaringsbehovIdent = request.hvemLøsteForrigeAvklaringsbehov()
                if (hvemLøsteForrigeAvklaringsbehovIdent != null) {
                    val avklaringsbehovReferanse = AvklaringsbehovReferanseDto(
                        oppgave.saksnummer,
                        oppgave.behandlingRef,
                        oppgave.journalpostId,
                        oppgave.avklaringsbehovKode
                    )
                    val reserverteOppgaver = ReserverOppgaveService(connection).reserverOppgave(avklaringsbehovReferanse, hvemLøsteForrigeAvklaringsbehovIdent, token())
                    if (reserverteOppgaver.isNotEmpty()) {
                        log.info("Ny oppgave($oppgaveId) ble automatisk tilordnet: $hvemLøsteForrigeAvklaringsbehovIdent")
                    }

                }
                oppgaveId
            }
            respond(oppgaveId)
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }
