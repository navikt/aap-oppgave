package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource

/**
 * Hent oppgaver reserver at innlogget bruker.
 */
fun NormalOpenAPIRoute.mineOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/mine-oppgaver").get<Unit, OppgavelisteRespons> {
        prometheus.httpCallCounter("/mine-oppgaver").increment()
        val mineOppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentMineOppgaver(ident())
        }
        respond(OppgavelisteRespons(antallTotalt = mineOppgaver.size, oppgaver = mineOppgaver.medPersonNavn(true, token())))
    }
