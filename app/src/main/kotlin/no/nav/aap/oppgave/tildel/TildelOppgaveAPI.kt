package no.nav.aap.oppgave.tildel

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.msgraph.MsGraphGateway
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoGateway
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.tilgang.Beslutter
import no.nav.aap.tilgang.Kvalitetssikrer
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.SaksbehandlerNasjonal
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import no.nav.aap.tilgang.authorizedGet
import no.nav.aap.tilgang.authorizedPost

fun NormalOpenAPIRoute.tildelOppgaveApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
    prometheus: PrometheusMeterRegistry,
    ansattInfoGateway: AnsattInfoGateway,
) {
    val msGraphClient = MsGraphGateway(prometheus)

    route("/saksbehandler-sok").authorizedPost<Unit, SaksbehandlerSøkResponse, SaksbehandlerSøkRequest>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        val saksbehandlereMedTilgang = dataSource.transaction { connection ->
            TildelOppgaveService(
                oppgaveRepository = OppgaveRepository(connection),
                msGraphClient = msGraphClient,
            ).søkEtterSaksbehandlere(
                søketekst = request.søketekst,
                oppgaver = request.oppgaver,
                enheter = request.enheter,
            )
        }

        respond(
            SaksbehandlerSøkResponse(
                saksbehandlere = saksbehandlereMedTilgang,
            )
        )
    }

    route("/tildel-oppgaver").authorizedPost<Unit, TildelOppgaveResponse, TildelOppgaveRequest>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        val tildelteOppgaver = dataSource.transaction { connection ->
            ReserverOppgaveService(connection, ansattInfoGateway)
                .tildelOppgaver(
                    oppgaver = request.oppgaver,
                    tildelTilIdent = request.saksbehandlerIdent,
                    tildeltAvIdent = ident()
                )
        }

        respond(
            TildelOppgaveResponse(
                oppgaver = tildelteOppgaver,
                tildeltTilSaksbehandler = request.saksbehandlerIdent
            )
        )

    }

    route("/{referanse}/tildelt-status").authorizedGet<BehandlingReferanse, TildeltStatusDto>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { request ->
        val oppgave = dataSource.transaction(readOnly = true) { connection ->
            OppgavelisteService(
                OppgaveRepository(connection),
                MarkeringRepository(connection),
                enhetService,
                norgGateway
            ).hentAktivOppgave(request)
        }

        if (oppgave != null) {
            respond(
                TildeltStatusDto(
                    tildeltSaksbehandlerIdent = oppgave.reservertAv,
                    tildeltSaksbehandlerNavn = oppgave.reservertAvNavn,
                    erTildeltInnloggetBruker = oppgave.reservertAv == ident()
                )
            )
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }
}

