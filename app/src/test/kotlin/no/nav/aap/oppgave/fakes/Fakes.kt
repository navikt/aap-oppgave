package no.nav.aap.oppgave.fakes

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.aap.oppgave.server.ErrorRespons
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgang.BehandlingTilgangRequest
import tilgang.TilgangResponse
import java.util.UUID

data class FakesConfig(
    var negativtSvarFraTilgangForBehandling: Set<UUID> = setOf()
)

class Fakes(azurePort: Int = 0, fakesConfig: FakesConfig = FakesConfig()) : AutoCloseable{
    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val azure = embeddedServer(Netty, port = azurePort, module = { azureFake() }).start()
    private val tilgang = embeddedServer(Netty, port = 0, module = { tilgangFake(fakesConfig) }).apply { start() }
    init {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
        // Azure
        System.setProperty("azure.openid.config.token.endpoint", "http://localhost:${azure.engine.port()}/token")
        System.setProperty("azure.app.client.id", "behandlingsflyt")
        System.setProperty("azure.app.client.secret", "")
        System.setProperty("azure.openid.config.jwks.uri", "http://localhost:${azure.engine.port()}/jwks")
        System.setProperty("azure.openid.config.issuer", "behandlingsflyt")
        // Tilgang
        System.setProperty("integrasjon.tilgang.url", "http://localhost:${tilgang.engine.port()}")
        System.setProperty("integrasjon.tilgang.scope", "scope")
        System.setProperty("integrasjon.tilgang.azp", "azp")

    }

    override fun close() {
        azure.stop(0L,0L)
    }

    private fun NettyApplicationEngine.port(): Int =
        runBlocking { resolvedConnectors() }
            .first { it.type == ConnectorType.HTTP }
            .port

    private fun Application.azureFake() {
        install(ContentNegotiation) {
            jackson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                this@azureFake.log.info("AZURE :: Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
                call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
            }
        }
        routing {
            post("/token") {
                val token = AzureTokenGen("behandlingsflyt", "behandlingsflyt").generate()
                call.respond(TestToken(access_token = token))
            }
            get("/jwks") {
                call.respond(AZURE_JWKS)
            }
        }
    }

    private fun Application.tilgangFake(fakesConfig: FakesConfig) = runBlocking {
        install(ContentNegotiation) {
            jackson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                this@tilgangFake.log.info(
                    "TILGANG :: Ukjent feil ved kall til '{}'",
                    call.request.local.uri,
                    cause
                )
                call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
            }
        }
        routing {
            post("/tilgang/behandling") {
                val req = call.receive<BehandlingTilgangRequest>()
                if (UUID.fromString(req.behandlingsreferanse) in fakesConfig.negativtSvarFraTilgangForBehandling)  {
                    call.respond(TilgangResponse(false))
                } else {
                    call.respond(TilgangResponse(true))
                }
            }
            post("/tilgang/journalpost") {
                call.respond(TilgangResponse(true))
            }
        }
    }

    internal data class TestToken(
        val access_token: String,
        val refresh_token: String = "very.secure.token",
        val id_token: String = "very.secure.token",
        val token_type: String = "token-type",
        val scope: String? = null,
        val expires_in: Int = 3599,
    )

    }