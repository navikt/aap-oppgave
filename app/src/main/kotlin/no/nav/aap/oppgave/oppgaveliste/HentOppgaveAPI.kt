package no.nav.aap.oppgave.oppgaveliste

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.SøkDto
import no.nav.aap.oppgave.SøkResponse
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import no.nav.aap.oppgave.plukk.TilgangGateway
import no.nav.aap.tilgang.Operasjon
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("hentOppgaveApi")

/**
 * Henter nyeste oppgave med status "OPPRETTET" gitt en behandlingsreferanse.
 */
fun NormalOpenAPIRoute.hentOppgaveApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry
) = route("/{referanse}/hent-oppgave").get<BehandlingReferanse, OppgaveDto> { request ->
    prometheus.httpCallCounter("/hent-oppgave").increment()
        val oppgave = dataSource.transaction(readOnly = true) { connection ->
            OppgavelisteService(
                OppgaveRepository(connection),
                MarkeringRepository(connection)
            ).hentAktivOppgave(request)
        }
    if (oppgave != null) {
        respond(oppgave.hentPersonNavn())
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
                harTilgang = true,
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

        respond(
            SøkResponse(
                oppgaver = oppgaver.hentPersonNavn(),
                harTilgang = harTilgang,
                harAdressebeskyttelse = harAdressebeskyttelse,
            )
        )
    }
}

private fun harAdressebeskyttelse(oppgave: OppgaveDto): Boolean =
    oppgave.enhet == Enhet.NAV_VIKAFOSSEN.kode ||
            oppgave.enhet.endsWith("83") || // alle kontorer for egen ansatt slutter på 83
            oppgave.harFortroligAdresse == true

private fun OppgaveDto.hentPersonNavn() = listOf(this).hentPersonNavn().first()
