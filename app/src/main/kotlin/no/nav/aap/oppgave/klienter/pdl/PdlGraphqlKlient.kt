package no.nav.aap.oppgave.klienter.pdl

import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.io.InputStream
import java.net.URI

interface IPdlKlient {
    fun hentAdressebeskyttelseOgGeolokasjon(personident: String, currentToken: OidcToken? = null): PdlData
    fun hentPersoninfoForIdenter(identer: List<String>): PdlData?
    fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData
}

class PdlGraphqlKlient(
    private val restClient: RestClient<InputStream>
): IPdlKlient {
    private val graphqlUrl = URI.create(requiredConfigForKey("integrasjon.pdl.url")).resolve("/graphql")

    companion object {
        private fun getClientConfig() = ClientConfig(
            scope = requiredConfigForKey("integrasjon.pdl.scope"),
        )

        private const val BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING = "B287"

        fun withClientCredentialsRestClient() =
            PdlGraphqlKlient(
                RestClient(
                    config = getClientConfig(),
                    tokenProvider = ClientCredentialsTokenProvider,
                    responseHandler = PdlResponseHandler(),
                    prometheus = prometheus
                )
            )

    }

    override fun hentAdressebeskyttelseOgGeolokasjon(personident: String, currentToken: OidcToken?): PdlData {
        val request = PdlRequest.hentAdressebeskyttelseOgGeografiskTilknytning(personident)
        val response = runBlocking { graphqlQuery(request, currentToken) }

        return response.data ?: error("Unexpected response from PDL: ${response.errors}")
    }

    override fun hentPersoninfoForIdenter(identer: List<String>): PdlData? {
        val request = PdlRequest.hentPersoninfoForIdenter(identer)
        val response = runBlocking { graphqlQuery(request) }
        return response.data ?: error("Unexpected response from PDL: ${response.errors}")
    }
    
    override fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData {
        val request = PdlRequest.hentAdressebeskyttelseForIdenter(identer)
        val response = runBlocking { graphqlQuery(request) }
        return response.data ?: error("Unexpected response from PDL: ${response.errors}")
    }

    private fun graphqlQuery(query: PdlRequest, currentToken: OidcToken? = null): PdlResponse {
        val request = PostRequest(
            query, currentToken = currentToken, additionalHeaders = listOf(
                Header("Accept", "application/json"),
                Header("TEMA", "AAP"),
                Header("Behandlingsnummer", BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING)
            )
        )
        return requireNotNull(restClient.post(uri = graphqlUrl, request))
    }
}