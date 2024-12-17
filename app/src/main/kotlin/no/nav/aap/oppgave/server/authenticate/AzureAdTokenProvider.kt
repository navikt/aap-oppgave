package no.nav.aap.oppgave.server.authenticate

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class AzureAdTokenProvider(
    private val config: AzureConfig,
    private val scope: String,
    private val client: HttpClient = defaultHttpClient,
) {
    val LOGGER: Logger = LoggerFactory.getLogger(AzureAdTokenProvider::class.java)

    suspend fun getUsernamePasswordToken(username: String, password: String) = getAccessToken(username) {
        "client_id=${config.clientId}&client_secret=${config.clientSecret}&scope=$scope&username=$username&password=$password&grant_type=password"
    }

    suspend fun getClientCredentialToken() = getAccessToken(scope) {
        "client_id=${config.clientId}&client_secret=${config.clientSecret}&scope=$scope&grant_type=client_credentials"
    }

    suspend fun getOnBehalfOfToken(accessToken: String) = getAccessToken(scope) {
        "client_id=${config.clientId}&client_secret=${config.clientSecret}&assertion=$accessToken&scope=$scope&grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&requested_token_use=on_behalf_of"
    }

    private val cache = TokenCache()

    private suspend fun getAccessToken(cacheKey: String, body: () -> String): String {
        val token = cache.get(cacheKey)
            ?: client.post(config.tokenEndpoint.toURL()) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body())
            }.also {
                if (!it.status.isSuccess()) {
                    LOGGER.warn("Feilet token-kall {}: {}", it.status.value, it.bodyAsText())
                }
            }.body<Token>().also {
                cache.add(cacheKey, it)
            }

        return token.access_token
    }

    private companion object {
        private val defaultHttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                }
            }
        }
    }
}