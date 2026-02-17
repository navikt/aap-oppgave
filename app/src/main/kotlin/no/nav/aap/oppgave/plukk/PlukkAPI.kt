package no.nav.aap.oppgave.plukk

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.msgraph.MsGraphGateway
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.tilgang.Beslutter
import no.nav.aap.tilgang.Kvalitetssikrer
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.SaksbehandlerNasjonal
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import no.nav.aap.tilgang.authorizedPost
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("plukkApi")

fun NormalOpenAPIRoute.plukkOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =
    route("/plukk-oppgave").authorizedPost<Unit, OppgaveDto, PlukkOppgaveDto>(
        RollerConfig(listOf(SaksbehandlerNasjonal, SaksbehandlerOppfolging, Beslutter, Kvalitetssikrer))
    ) { _, request ->
        prometheus.httpCallCounter("/plukk-oppgave").increment()
        val enhetService = EnhetService(msGraphClient = MsGraphGateway(prometheus))
        val oppgave = dataSource.transaction { connection ->
            PlukkOppgaveService(
                enhetService,
                OppgaveRepository(connection),
                FlytJobbRepository(connection),
            ).plukkOppgave(
                OppgaveId(request.oppgaveId, request.versjon),
                ident(),
                token()
            )
        }
        if (oppgave != null) {
            respond(oppgave)
        } else {
            log.info("Bruker kunne ikke plukke oppgave")
            respondWithStatus(HttpStatusCode.Unauthorized)
        }
    }