package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.metriker.httpCallCounter
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.verdityper.OppgaveId
import javax.sql.DataSource

fun NormalOpenAPIRoute.mineOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/mine-oppgaver").get<Unit, List<OppgaveDto>> {
        prometheus.httpCallCounter("/mine-oppgaver").increment()
        val mineOppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentMineOppgaver(ident())
        }
        respond(mineOppgaver)
    }

fun NormalOpenAPIRoute.avsluttOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/avslutt-oppgave").post<Unit, List<OppgaveId>, AvklaringsbehovReferanseDto> { _, dto ->
        prometheus.httpCallCounter("/avslutt-oppgave").increment()
        val oppgaver = dataSource.transaction { connection ->
            val innloggetBrukerIdent = ident()
            val oppgaverSomSkalAvsluttes = OppgaveRepository(connection).hentÅpneOppgaver(dto)
            oppgaverSomSkalAvsluttes.forEach {
                OppgaveRepository(connection).avsluttOppgave(it,innloggetBrukerIdent)
            }
            oppgaverSomSkalAvsluttes
        }
        respond(oppgaver)
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