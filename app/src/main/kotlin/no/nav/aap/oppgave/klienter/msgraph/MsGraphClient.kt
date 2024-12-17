package no.nav.aap.oppgave.klienter.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.jackson
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.oppgave.server.authenticate.AzureAdTokenProvider
import java.util.*

interface IMsGraphClient {
    suspend fun hentAdGrupper(currentToken: String, ident: String): MemberOf
}

class MsGraphClient(
    azureConfig: AzureConfig,
) : IMsGraphClient {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(
        azureConfig,
        requiredConfigForKey("MS_GRAPH_SCOPE"),
    )

    override suspend fun hentAdGrupper(currentToken: String, ident: String): MemberOf {
        val graphToken = azureTokenProvider.getOnBehalfOfToken(currentToken)

        val baseUrl = requiredConfigForKey("MS_GRAPH_BASE_URL")
        val respons = httpClient.get("$baseUrl/me/memberOf") {
            bearerAuth(graphToken)
            contentType(ContentType.Application.Json)
        }

        return when (respons.status) {
            HttpStatusCode.OK -> {
                respons.body()
            }

            else -> throw MsGraphException("Feil fra Microsoft Graph: ${respons.status} : ${respons.bodyAsText()}")
        }
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
