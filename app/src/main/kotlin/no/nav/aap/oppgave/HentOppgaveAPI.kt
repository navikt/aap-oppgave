package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.metrikker.httpCallCounter
import javax.sql.DataSource

/**
 * Henter en oppgave gitt en behandling knyttet til en sak i behandlingsflyt eller en joournalpost i postmottk.
 */
fun NormalOpenAPIRoute.hentOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/hent-oppgave").post<Unit, OppgaveDto, AvklaringsbehovReferanseDto> { _, request ->
        prometheus.httpCallCounter("/hent-oppgave").increment()
        val oppgave = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentOppgave(request)
        }
        if (oppgave != null) {
            respond(oppgave.medPersonNavn(token()))
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }

/**
 * Fritekstsøk etter oppgave. Hvis søketekster er 11 tegn så søkes det etter oppgaver knyttet til fødselsnummer.
 * Ellers søkers det etter oppgave knyttet til saksnummer.
 */
fun NormalOpenAPIRoute.søkApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/sok").post<Unit, List<OppgaveDto>, SøkDto> { _, søk ->
        prometheus.httpCallCounter("/sok").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            val søketekst =  søk.søketekst.trim()
            val oppgaveRepo = OppgaveRepository(connection)
            if (søketekst.length >= 11) {
                oppgaveRepo.finnOppgaverGittPersonident(søketekst)
            } else {
                oppgaveRepo.finnOppgaverGittSaksnummer(søketekst.uppercase())
            }
        }
        respond(oppgaver.medPersonNavn(true, token()))
    }


private fun OppgaveDto.medPersonNavn(token: OidcToken): OppgaveDto {
    return listOf(this).medPersonNavn(true, token).first()
}
