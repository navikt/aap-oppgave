package no.nav.aap.oppgave.oppgaveliste

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.SøkDto
import no.nav.aap.oppgave.SøkResponse
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.plukk.TilgangGateway
import no.nav.aap.tilgang.Operasjon
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("hentOppgaveApi")

/**
 * Henter en oppgave gitt en behandling knyttet til en sak i behandlingsflyt eller en journalpost i postmottak.
 */
fun NormalOpenAPIRoute.hentOppgaveApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry
) = route("/hent-oppgave").post<Unit, OppgaveDto, AvklaringsbehovReferanseDto> { _, request ->
    prometheus.httpCallCounter("/hent-oppgave").increment()
    val oppgave =
        dataSource.transaction(readOnly = true) { connection ->
            OppgavelisteService(
                OppgaveRepository(connection),
                MarkeringRepository(connection)
            ).hentOppgave(request)
        }
    if (oppgave != null) {
        respond(oppgave.hentPersonNavnMedTilgangssjekk(token()))
    } else {
        respondWithStatus(HttpStatusCode.NoContent)
    }
}

/**
 * Fritekstsøk etter oppgave. Hvis søketekster er 11 tegn så søkes det etter oppgaver knyttet til fødselsnummer.
 * Ellers søkers det etter oppgave knyttet til saksnummer.
 */
fun NormalOpenAPIRoute.søkApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry
) {
    route("/sok").post<Unit, SøkResponse, SøkDto> { _, søk ->
        prometheus.httpCallCounter("/sok").increment()
        val oppgaver =
            dataSource.transaction(readOnly = true) { connection ->
                val søketekst = søk.søketekst.trim()
                OppgavelisteService(
                    OppgaveRepository(connection),
                    MarkeringRepository(connection)
                ).søkEtterOppgaver(søketekst)
            }

        if (oppgaver.isEmpty()) {
            log.info("Fant ingen oppgaver basert på søketeksten")
            respond(SøkResponse(
                oppgaver = emptyList(),
                harTilgang = false,
                harAdressebeskyttelse = false,
            ))
        }
        val harAdressebeskyttelse = oppgaver.any { harAdressebeskyttelse(it) }
        val harTilgang =
            oppgaver.all {
                TilgangGateway.sjekkTilgang(
                    it.tilAvklaringsbehovReferanseDto(),
                    token(),
                    Operasjon.SE
                )
            }

        val oppgaverMedTilgangSjekk = if (harAdressebeskyttelse) {
            // Sensurerer felter om saksbehandler ikke har tilgang
            oppgaver.hentPersonNavnMedTilgangssjekk(token(), operasjon = Operasjon.SE)
        } else {
            oppgaver.hentPersonNavn()
        }
        respond(
            SøkResponse(
                oppgaver = oppgaverMedTilgangSjekk,
                harTilgang = harTilgang,
                harAdressebeskyttelse = harAdressebeskyttelse,
            )
        )
    }
}

private fun OppgaveDto.hentPersonNavnMedTilgangssjekk(token: OidcToken): OppgaveDto =
    listOf(this).hentPersonNavnMedTilgangssjekk(token).first()