package no.nav.aap.oppgave.oppgaveliste

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.OppgaveEnhetResponse
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.tilgang.AuthorizationParamPathConfig
import no.nav.aap.tilgang.authorizedGet
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentOppgavEnhetApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry
) = route("/{referanse}/hent-oppgave-enhet").authorizedGet<BehandlingReferanse, OppgaveEnhetResponse>(
    AuthorizationParamPathConfig(
        applicationRole = "hent-oppgave-enhet",
        applicationsOnly = true
    )
) { behandlingReferanse ->
    prometheus.httpCallCounter("/hent-oppgave-enhet").increment()
    val oppgaver = dataSource.transaction(readOnly = true) { connection ->
        OppgavelisteService(
            OppgaveRepository(connection),
            MarkeringRepository(connection)
        ).hentOppgaveEnhetListe(behandlingReferanse)
    }

    respond(OppgaveEnhetResponse(oppgaver))
}
