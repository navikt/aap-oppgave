package no.nav.aap.oppgave.oppgaveliste

import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource

data class MineOppgaverRequest(@param:QueryParam("Vis kun på vent-oppgaver.") val kunPaaVent: Boolean? = false)

/**
 * Hent oppgaver reserver at innlogget bruker.
 */
fun NormalOpenAPIRoute.mineOppgaverApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry
) = route("/mine-oppgaver").get<MineOppgaverRequest, OppgavelisteRespons> { req ->
    prometheus.httpCallCounter("/mine-oppgaver").increment()
    val mineOppgaver =
        dataSource.transaction(readOnly = true) { connection ->
            OppgavelisteService(
                OppgaveRepository(connection),
                MarkeringRepository(connection)
            ).hentMineOppgaver(ident(), req.kunPaaVent)
        }
    respond(
        OppgavelisteRespons(
            antallTotalt = mineOppgaver.size,
            oppgaver = mineOppgaver
        )
    )
}