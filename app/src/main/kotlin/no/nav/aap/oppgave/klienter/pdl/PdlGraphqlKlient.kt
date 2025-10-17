package no.nav.aap.oppgave.klienter.pdl

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
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
import java.time.Duration

interface IPdlKlient {
    fun hentAdressebeskyttelseOgGeolokasjon(personident: String, currentToken: OidcToken? = null): PdlData
    fun hentPersoninfoForIdenter(identer: List<String>): List<HentPersonBolkResult>
    fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData
}

class PdlGraphqlKlient(
    private val restClient: RestClient<InputStream>
) : IPdlKlient {
    private val graphqlUrl = URI.create(requiredConfigForKey("integrasjon.pdl.url")).resolve("/graphql")

    init {
        CaffeineCacheMetrics.monitor(prometheus, personinfoCache, "pdl_personinfo")
    }

    companion object {
        private fun getClientConfig() = ClientConfig(
            scope = requiredConfigForKey("integrasjon.pdl.scope"),
        )

        private const val BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING = "B287"

        private val personinfoCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats()
            .build<String, HentPersonBolkResult>()

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
        val response = graphqlQuery(request, currentToken)

        return response.data ?: error("Unexpected response from PDL: ${response.errors}")
    }

    override fun hentPersoninfoForIdenter(identer: List<String>): List<HentPersonBolkResult> {
        val (identerMangler, identerICache) = identer.partition { personinfoCache.getIfPresent(it) == null }

        val fraCache = identerICache.mapNotNull(personinfoCache::getIfPresent)
        if (identerMangler.isEmpty()) {
            return fraCache
        }

        val request = PdlRequest.hentPersoninfoForIdenter(identerMangler)
        val response = graphqlQuery(request)

        requireNotNull(response.data) {
            "Ukjent respons fra PDL: ${response.errors}"
        }

        val res = response.data.hentPersonBolk.orEmpty()
            .onEach { personinfoCache.put(it.ident, it) }

        return res + fraCache
    }

    override fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData {
        val request = PdlRequest.hentAdressebeskyttelseForIdenter(identer)
        val response = graphqlQuery(request)
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