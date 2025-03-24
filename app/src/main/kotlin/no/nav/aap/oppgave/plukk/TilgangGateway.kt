package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.TilgangResponse
import org.slf4j.LoggerFactory
import java.net.URI

object TilgangGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgang.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.tilgang.scope"))
    private val log = LoggerFactory.getLogger(TilgangGateway::class.java)

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = OnBehalfOfTokenProvider,
        prometheus = prometheus
    )
    
    fun sjekkTilgang(avklaringsbehovReferanse: AvklaringsbehovReferanseDto, token: OidcToken): Boolean {
        try {
            return if (avklaringsbehovReferanse.journalpostId != null) {
                harTilgangTilJournalpost(
                    JournalpostTilgangRequest(
                        journalpostId = avklaringsbehovReferanse.journalpostId!!,
                        avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                        operasjon = Operasjon.SAKSBEHANDLE
                    ), token
                )
            } else {
                harTilgangTilBehandling(
                    BehandlingTilgangRequest(
                        behandlingsreferanse = avklaringsbehovReferanse.referanse!!,
                        avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                        operasjon = Operasjon.SAKSBEHANDLE
                    ), token
                )
            }
        } catch (e: Exception) {
            // TODO: Undersøk håndtering av dette - bør kanskje ikke alltid returnere false da dette kan føre til unødvendig mange kall til tilgang
            log.info("Fikk feil mot tilgang: $e - returnerer false")
            return false
        }
    }

    private fun harTilgangTilBehandling(body: BehandlingTilgangRequest, currentToken: OidcToken): Boolean {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/behandling"),
                request = httpRequest
            )
        )
        return respons.tilgang
    }

    private fun harTilgangTilJournalpost(body: JournalpostTilgangRequest, currentToken: OidcToken): Boolean {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/journalpost"),
                request = httpRequest
            )
        )
        return respons.tilgang
    }
}