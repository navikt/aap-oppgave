package no.nav.aap.oppgave.plukk

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.exception.ApiException
import javax.sql.DataSource
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.OppgaveDto
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
import org.slf4j.LoggerFactory
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.Plukket
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.Avsluttet
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.IngenTilgang
import no.nav.aap.oppgave.plukk.PlukkOppgaveService.PlukkResult.AlleredeTildelt

private val log = LoggerFactory.getLogger("plukkApi")

fun NormalOpenAPIRoute.plukkOppgaveApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry,
    enhetService: EnhetService,
    ansattInfoGateway: AnsattInfoGateway,
) {
    route("/plukk-oppgave").authorizedPost<Unit, OppgaveDto, PlukkOppgaveDto>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/plukk-oppgave").increment()
        val oppgave = PlukkOppgaveService.plukkOppgave(
            dataSource,
            enhetService,
            ansattInfoGateway,
            token(),
            ident(),
            request.oppgaveId,
            versjon = request.versjon,
        )

        when (
            val result = PlukkOppgaveService.plukkOppgave(
                dataSource = dataSource,
                enhetService = enhetService,
                ansattInfoGateway = ansattInfoGateway,
                token = token(),
                ident = ident(),
                oppgaveId = request.oppgaveId,
                versjon = request.versjon,
            )
        ) {
            is Plukket -> respond(result.oppgave.tilOppgaveDto())
            IngenTilgang -> {
                log.info("Bruker kunne ikke plukke oppgave grunnet manglende tilgang")
                respondWithStatus(HttpStatusCode.Unauthorized)
            }

            AlleredeTildelt -> throw ApiException(
                status = HttpStatusCode.Conflict,
                message = "Oppgaven er allerede tildelt."
            )

           Avsluttet -> throw ApiException(
                status = HttpStatusCode.Conflict,
                message = "Oppgaven er avsluttet."
            )
        }
    }
}