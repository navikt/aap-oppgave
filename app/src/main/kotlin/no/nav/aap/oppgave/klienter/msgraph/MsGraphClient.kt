package no.nav.aap.oppgave.klienter.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import java.net.URI
import java.util.*

interface IMsGraphClient {
    fun hentAdGrupper(currentToken: String, ident: String): MemberOf
}

class MsGraphClient(
    prometheus: PrometheusMeterRegistry
) : IMsGraphClient {
    private val baseUrl = URI.create(requiredConfigForKey("ms.graph.base.url"))

    private val clientConfig = ClientConfig(
        scope = requiredConfigForKey("ms.graph.scope")
    )
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = clientConfig,
        tokenProvider = OnBehalfOfTokenProvider,
        prometheus = prometheus,
    )

    override fun hentAdGrupper(currentToken: String, ident: String): MemberOf {
        val url = baseUrl.resolve("me/memberOf")
        val respons = httpClient.get<MemberOf>(url, GetRequest(currentToken = OidcToken(currentToken))) ?: MemberOf()
        return respons
    }

    companion object {
        private const val MSGRAPH_PREFIX = "msgraph"
    }
}

data class MemberOf(
    @JsonProperty("value")
    val groups: List<Group> = emptyList()
)

data class Group(
    @JsonProperty("id")
    val id: UUID,
    @JsonProperty("mailNickname")
    val name: String
)

internal object HttpClientFactory {
    fun create(): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 5_000
        }
        install(HttpRequestRetry)

        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }
    }
}
