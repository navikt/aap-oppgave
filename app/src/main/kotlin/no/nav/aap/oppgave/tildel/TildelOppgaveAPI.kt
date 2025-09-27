package no.nav.aap.oppgave.tildel

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.tilgang.Beslutter
import no.nav.aap.tilgang.Kvalitetssikrer
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.SaksbehandlerNasjonal
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import no.nav.aap.tilgang.authorizedPost
import javax.sql.DataSource

fun NormalOpenAPIRoute.tildelOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) {
    route("/saksbehandler-sok").authorizedPost<Unit, SaksbehandlerSøkResponse, SaksbehandlerSøkRequest>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/saksbehandler-sok").increment()

        val saksbehandlereFraNom = dataSource.transaction { connection ->
            TildelOppgaveService(
                oppgaveRepository = OppgaveRepository(connection),
            ).søkEtterSaksbehandlere(
                søketekst = request.søketekst,
                oppgaveId = request.oppgaveId,
            )
        }

        respond(
            SaksbehandlerSøkResponse(
                saksbehandlere = saksbehandlereFraNom.map {
                    SaksbehandlerDto(
                        navn = it.visningsnavn,
                        navIdent = it.navident
                    )
                }
            )
        )
    }

    route("/tildel-oppgaver").authorizedPost<Unit, TildelOppgaveResponse, TildelOppgaveRequest>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/tildel-oppgaver").increment()

        val tildelteOppgaver = dataSource.transaction { connection ->
            ReserverOppgaveService(
                oppgaveRepository = OppgaveRepository(connection),
                flytJobbRepository = FlytJobbRepository(connection),
            ).tildelOppgaver(oppgaver = request.oppgaver, ident = request.saksbehandlerIdent, tildeltAvIdent = ident())
        }

        respond(
            TildelOppgaveResponse(
                oppgaver = tildelteOppgaver,
                tildeltTilSaksbehandler = request.saksbehandlerIdent
            )
        )

    }
}

