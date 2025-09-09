package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.tokenx.OnBehalfOfTokenProvider as TexasOnBehalfOfTokenProvider
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.TilgangResponse
import java.net.URI

object TilgangGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgang.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.tilgang.scope"))

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = if (Miljø.erProd()) OnBehalfOfTokenProvider else TexasOnBehalfOfTokenProvider(identityProvider = "azuread"),
        prometheus = prometheus
    )

    fun sjekkTilgang(avklaringsbehovReferanse: AvklaringsbehovReferanseDto, token: OidcToken, operasjon: Operasjon = Operasjon.SAKSBEHANDLE): Boolean {
        return if (avklaringsbehovReferanse.journalpostId != null) {
            harTilgangTilJournalpost(
                JournalpostTilgangRequest(
                    journalpostId = avklaringsbehovReferanse.journalpostId!!,
                    avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = operasjon
                ), token
            )
        } else {
            harTilgangTilBehandling(
                BehandlingTilgangRequest(
                    behandlingsreferanse = avklaringsbehovReferanse.referanse!!,
                    avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = operasjon
                ), token
            )
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