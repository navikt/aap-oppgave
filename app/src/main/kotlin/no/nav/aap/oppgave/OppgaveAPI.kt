package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metriker.httpCallCounter
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

    route("/avslutt-oppgave").post<Unit, List<OppgaveId>, AvsluttOppgaveDto> { _, dto ->
        prometheus.httpCallCounter("/avslutt-oppgave").increment()
        val oppgaver = dataSource.transaction { connection ->
            val oppgaverSomSkalAvsluttes = OppgaveRepository(connection).hentOppgaverForReferanse(
                dto.saksnummer,
                dto.referanse,
                dto.journalpostId,
                dto.avklaringsbehovtype,
                ident()
            )
            oppgaverSomSkalAvsluttes.forEach {
                OppgaveRepository(connection).avsluttOppgave(it)
            }
            oppgaverSomSkalAvsluttes
        }
        respond(oppgaver)
    }