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
import no.nav.aap.oppgave.filter.FilterId
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.filter.OppgaveSøkDto
import no.nav.aap.oppgave.filter.TransientFilterDto
import no.nav.aap.oppgave.metrikker.httpCallCounter
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

fun NormalOpenAPIRoute.hentOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/hent-oppgaver").post<Unit, List<OppgaveDto>, FilterId> { _, request ->
        prometheus.httpCallCounter("/hent-oppgave").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            val filter = FilterRepository(connection).hent(request.filterId)
            OppgaveRepository(connection).finnOppgaver(filter!!)
        }
        respond(oppgaver)
    }

fun NormalOpenAPIRoute.hentOppgavelisteApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/oppgaveliste").post<Unit, List<OppgaveDto>, OppgaveSøkDto> { _, request ->
        prometheus.httpCallCounter("/oppgaveliste").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            val filter = FilterRepository(connection).hent(request.filterId)
            OppgaveRepository(connection).finnOppgaver(filter!!.copy(enheter = request.enheter))
        }
        respond(oppgaver)
    }


fun NormalOpenAPIRoute.oppgavesøkApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/oppgavesok/").post<Unit, List<OppgaveDto>, TransientFilterDto> { _, filter ->
        prometheus.httpCallCounter("/oppgavesok").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaver(filter)
        }
        respond(oppgaver)
    }
