package no.nav.aap.oppgave.oppgaveliste

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.liste.MineOppgaverRequest
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.liste.OppgavelisteResponsV2
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.tilListeOppgaveResponse
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource


/**
 * Hent oppgaver reservert til innlogget bruker.
 */
fun NormalOpenAPIRoute.mineOppgaverApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
    prometheus: PrometheusMeterRegistry
) {
    route("/mine-oppgaver").get<MineOppgaverRequest, OppgavelisteRespons> { req ->
        prometheus.httpCallCounter("/mine-oppgaver").increment()
        val mineOppgaver =
            dataSource.transaction(readOnly = true) { connection ->
                OppgavelisteService(
                    OppgaveRepository(connection),
                    MarkeringRepository(connection),
                    enhetService,
                    norgGateway
                ).hentMineOppgaver(
                    ident = ident(),
                    kunPaaVent = req.kunPaaVent,
                    sortBy = req.sortby,
                    sortOrder = req.sortorder,
                )
            }
        respond(
            OppgavelisteRespons(
                antallTotalt = mineOppgaver.size,
                oppgaver = mineOppgaver.map { it.tilOppgaveDto() }
            )
        )
    }

    route("/mine-oppgaver/v2").get<MineOppgaverRequest, OppgavelisteResponsV2> { req ->
        prometheus.httpCallCounter("/mine-oppgaver").increment()
        val mineOppgaver =
            dataSource.transaction(readOnly = true) { connection ->
                OppgavelisteService(
                    OppgaveRepository(connection),
                    MarkeringRepository(connection),
                    enhetService,
                    norgGateway
                ).hentMineOppgaver(
                    ident = ident(),
                    kunPaaVent = req.kunPaaVent,
                    sortBy = req.sortby,
                    sortOrder = req.sortorder,
                )
            }
        respond(
            OppgavelisteResponsV2(
                antallTotalt = mineOppgaver.size,
                oppgaver = mineOppgaver.map { it.tilListeOppgaveResponse() }
            )
        )
    }
}