package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.verdityper.Status
import javax.sql.DataSource

fun NormalOpenAPIRoute.avreserverOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) {
    route("/avreserver-oppgaver").post<Unit, List<OppgaveId>, AvreserverOppgaveDto> { _, dto ->
        prometheus.httpCallCounter("avreserver-oppgaver").increment()
        val oppgaver = dataSource.transaction { connection ->
            val oppgaverSomSkalAvreserveres = dto.oppgaver.map { oppgaveId -> OppgaveRepository(connection)
                    .hentOppgave(oppgaveId) }
                    .filter { it.reservertAv != null && it.status != Status.AVSLUTTET }
                    .map { OppgaveId(requireNotNull(it.id), it.versjon) }
            val reserverOppgaveService = ReserverOppgaveService(
                OppgaveRepository(connection),
                FlytJobbRepository(connection),
            )
            val ident = ident()
            oppgaverSomSkalAvreserveres.forEach {
                reserverOppgaveService.avreserverOppgave(it, ident)
            }
            oppgaverSomSkalAvreserveres
        }
        respond(oppgaver)
    }
}

fun NormalOpenAPIRoute.flyttOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/flytt-oppgave").post<Unit, List<OppgaveId>, FlyttOppgaveDto> { _, dto ->
        prometheus.httpCallCounter("flytt-oppgave").increment()

        val oppgaver = dataSource.transaction { connection ->
            val innloggetBrukerIdent = ident()
            val token = token()
            val reserverOppgaveService = ReserverOppgaveService(
                OppgaveRepository(connection),
                FlytJobbRepository(connection),
            )
            reserverOppgaveService.reserverOppgave(dto.avklaringsbehovReferanse, innloggetBrukerIdent, token)
        }
        respond(oppgaver)
    }