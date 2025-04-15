package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.miljo.MiljøKode
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.filter.TransientFilterDto
import no.nav.aap.oppgave.klienter.msgraph.MsGraphClient
import no.nav.aap.oppgave.liste.OppgavelisteRequest
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("oppgavelisteApi")

/**
 * Søker etter oppgaver med et predefinert filter angitt med filterId. Det vil bli sjekket om innlogget bruker har tilgang
 * til oppgavene. I tillegg kan det legges på en begrensning på enheter.
 */
fun NormalOpenAPIRoute.oppgavelisteApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =
    route("/oppgaveliste").post<Unit, OppgavelisteRespons, OppgavelisteRequest> { _, request ->
        prometheus.httpCallCounter("/oppgaveliste").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            val filter = requireNotNull(FilterRepository(connection).hent(request.filterId))
            val veilederIdent = if (request.veileder) {
                ident()
            } else {
                null
            }
            val rekkefølge = when (Miljø.er()) {
                MiljøKode.DEV -> OppgaveRepository.Rekkefølge.desc
                else -> OppgaveRepository.Rekkefølge.asc
            }
            OppgaveRepository(connection).finnOppgaver(
                filter.copy(enheter = request.enheter, veileder = veilederIdent),
                rekkefølge
            )
        }

        val enhetService = EnhetService(MsGraphClient(prometheus))
        val token = token()
        val enhetsGrupper = enhetService.hentEnheter(token.token(), ident())
        val oppgaverMedTilgang = oppgaver.asSequence().filter {
            enhetsGrupper.contains(it.enhet)
        }.take(request.maxAntall).toList()

        respond(
            OppgavelisteRespons(
                antallTotalt = oppgaverMedTilgang.size,
                oppgaver = oppgaverMedTilgang.medPersonNavn(false, token())
            )
        )
    }

/**
 * Søker etter oppgaver med et fritt definert filter som ikke trenger være lagret i database.
 */
fun NormalOpenAPIRoute.oppgavesøkApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/oppgavesok").post<Unit, List<OppgaveDto>, TransientFilterDto> { _, filter ->
        prometheus.httpCallCounter("/oppgavesok").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaver(filter)
        }
        respond(
            oppgaver.medPersonNavn(
                true,
                token()
            )
        ) //TODO: vurder om vi må ha true her, eller om vi må gjøre om dette.
    }