package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.metriker.httpCallCounter
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource

fun NormalOpenAPIRoute.mineOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/mine-oppgaver").get<Unit, List<OppgaveDto>> {
        prometheus.httpCallCounter("/mine-oppgaver").increment()
        val mineOppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentMineOppgaver(ident())
        }
        respond(mineOppgaver)
    }

fun NormalOpenAPIRoute.alleÅpneOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry)  =

    route("alle-oppgaver").get<Unit, List<OppgaveDto>> {
        prometheus.httpCallCounter("/alle-oppgaver").increment()
        val mineOppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentAlleÅpneOppgaver()
        }
        respond(mineOppgaver)
    }

fun NormalOpenAPIRoute.hentOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/hent-oppgave").post<Unit, OppgaveDto, AvklaringsbehovReferanseDto> { _, request ->
        prometheus.httpCallCounter("/hent-oppgave").increment()
        val oppgave = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentOppgave(request)


        }
        if (oppgave != null) {
            respond(oppgave)
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }

    }

fun NormalOpenAPIRoute.avreserverOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/avreserver-oppgave").post<Unit, List<OppgaveId>, AvklaringsbehovReferanseDto> { _, dto ->
        prometheus.httpCallCounter("avreserver-oppgave").increment()
        val oppgaver = dataSource.transaction { connection ->
            val oppgaverSomSkalAvreserveres = OppgaveRepository(connection).hentÅpneOppgaver(dto)
            oppgaverSomSkalAvreserveres.forEach {
                OppgaveRepository(connection).avreserverOppgave(it, ident())
            }
            oppgaverSomSkalAvreserveres
        }
        respond(oppgaver)
    }


fun NormalOpenAPIRoute.flyttOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/flytt-oppgave").post<Unit, List<OppgaveId>, FlyttOppgaveDto> { _, dto ->
        prometheus.httpCallCounter("flytt-oppgave").increment()

        val oppgaver = dataSource.transaction { connection ->
            val innloggetBrukerIdent = ident()
            val token = token()
            val reserverOppgaveService = ReserverOppgaveService(connection)
            reserverOppgaveService.reserverOppgave(dto.avklaringsbehovReferanse, innloggetBrukerIdent, token)
        }
        respond(oppgaver)

    }