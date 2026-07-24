package no.nav.aap.oppgave.plukk

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoGateway
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.tilgang.Beslutter
import no.nav.aap.tilgang.Kvalitetssikrer
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.SaksbehandlerNasjonal
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import no.nav.aap.tilgang.authorizedPost
import javax.sql.DataSource


fun NormalOpenAPIRoute.plukkOppgaveApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry,
    enhetService: EnhetService,
    ansattInfoGateway: AnsattInfoGateway,
) {
    route("/plukk-oppgave").authorizedPost<Unit, PlukkOppgaveResponse, PlukkOppgaveRequest>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/plukk-oppgave").increment()
        val plukketOppgave = PlukkOppgaveService.plukkOppgave(
            dataSource,
            enhetService,
            ansattInfoGateway,
            token(),
            ident(),
            request.oppgaveId
        )
        if (plukketOppgave != null) {
            respond(
                PlukkOppgaveResponse(
                    BehandlingskontekstResponse(
                        behandlingsreferanse = plukketOppgave.behandlingRef,
                        saksnummer = plukketOppgave.saksnummer,
                        journalpostId = plukketOppgave.journalpostId,
                        behandlingstype = plukketOppgave.behandlingstype,
                        tilbakekrevingUrl = plukketOppgave.tilbakekrevingsVars?.tilbakekrevings_URL
                    )
                )
            )
        } else {
            respondWithStatus(HttpStatusCode.Forbidden)
        }
    }

    route("/plukk-oppgave/v2").authorizedPost<Unit, PlukkOppgaveResponse, PlukkOppgaveRequest>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/plukk-oppgave/v2").increment()
        val plukketOppgave = PlukkOppgaveService.plukkOppgave(
            dataSource,
            enhetService,
            ansattInfoGateway,
            token(),
            ident(),
            request.oppgaveId
        )
        if (plukketOppgave != null) {
            respond(
                PlukkOppgaveResponse(
                    BehandlingskontekstResponse(
                        behandlingsreferanse = plukketOppgave.behandlingRef,
                        saksnummer = plukketOppgave.saksnummer,
                        journalpostId = plukketOppgave.journalpostId,
                        behandlingstype = plukketOppgave.behandlingstype,
                        tilbakekrevingUrl = plukketOppgave.tilbakekrevingsVars?.tilbakekrevings_URL
                    )
                )
            )
        } else {
            respondWithStatus(HttpStatusCode.Unauthorized)
        }
    }
}