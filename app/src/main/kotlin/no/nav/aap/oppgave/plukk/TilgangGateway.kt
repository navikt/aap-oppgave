package no.nav.aap.oppgave.plukk

import tilgang.TilgangRequest
import tilgang.TilgangResponse
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.komponenter.config.requiredConfigForKey
import java.net.URI

object TilgangGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgang.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.tilgang.scope"))

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = OnBehalfOfTokenProvider,
    )

    fun harTilgang(body: TilgangRequest, currentToken: OidcToken): Boolean {
        val respons = query(
            body,
            currentToken = currentToken
        )
        return respons.tilgang
    }

    private fun query(body: TilgangRequest, currentToken: OidcToken): TilgangResponse {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        return requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang"),
                request = httpRequest
            )
        )
    }
}