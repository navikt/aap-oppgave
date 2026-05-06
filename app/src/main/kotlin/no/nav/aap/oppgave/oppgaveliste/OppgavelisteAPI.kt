package no.nav.aap.oppgave.oppgaveliste

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import javax.sql.DataSource
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.liste.OppgavelisteRequest
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import no.nav.aap.oppgave.server.authenticate.ident
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("oppgavelisteApi")

/**
 * Søker etter oppgaver med et predefinert filter angitt med filterId. Det vil bli sjekket om innlogget bruker har tilgang
 * til oppgavene. I tillegg kan det legges på en begrensning på enheter.
 */
fun NormalOpenAPIRoute.oppgavelisteApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
    prometheus: PrometheusMeterRegistry,
) {
    route("/oppgaveliste").post<Unit, OppgavelisteRespons, OppgavelisteRequest> { _, request ->
        prometheus.httpCallCounter("/oppgaveliste").increment()
        val (data, bruktBehanlingstyperIFilter) =
            dataSource.transaction(readOnly = true) { connection ->
                log.info("Henter filter med filterId ${request.filterId}")
                val filter =
                    requireNotNull(FilterRepository(connection).hent(request.filterId)) { "filter kan ikke være null. filterId: ${request.filterId}" }
                val veilederIdent =
                    if (request.veileder) {
                        ident()
                    } else {
                        null
                    }
                Pair(OppgavelisteService(
                    oppgaveRepository = OppgaveRepository(connection),
                    markeringRepository = MarkeringRepository(connection),
                    norgGateway = norgGateway,
                    enhetService = enhetService,
                ).hentOppgaverMedTilgang(
                    request.utvidetFilter,
                    request.enheter,
                    request.paging,
                    request.kunLedigeOppgaver == true,
                    filter,
                    veilederIdent,
                    token(),
                    ident(),
                    request.sortering?.sortBy,
                    request.sortering?.sortOrder,
                    hastemarkeringerFørst = request.hastemarkeringerFørst == true
                ), filter.behandlingstyper)
            }

        respond(
            OppgavelisteRespons(
                antallTotalt = data.antallTotalt,
                oppgaver = data.oppgaver.hentPersonNavn(),
                antallGjenstaaende = data.antallGjenstaaende,
                sattFilterBehandlingstyper = bruktBehanlingstyperIFilter
            )
        )
    }
}
