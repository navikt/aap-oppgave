package no.nav.aap.oppgave.fakes

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

data class FakesConfig(
    var negativtSvarFraTilgangForBehandling: Set<UUID> = setOf()
)

class Fakes(fakesConfig: FakesConfig = FakesConfig()): AutoCloseable {

    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val azure = FakeServer(module = { azureFake() })
    private val tilgang = FakeServer(module = { tilgangFake(fakesConfig) })
    private val pdl = FakeServer(module = { pdlFake() })
    private val norg = FakeServer(module = { norgFake() })

    init {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
        // Azure
        System.setProperty("azure.openid.config.token.endpoint", "http://localhost:${azure.port()}/token")
        System.setProperty("azure.app.client.id", "behandlingsflyt")
        System.setProperty("azure.app.client.secret", "")
        System.setProperty("azure.openid.config.jwks.uri", "http://localhost:${azure.port()}/jwks")
        System.setProperty("azure.openid.config.issuer", "behandlingsflyt")
        // Tilgang
        System.setProperty("integrasjon.tilgang.url", "http://localhost:${tilgang.port()}")
        System.setProperty("integrasjon.tilgang.scope", "scope")
        System.setProperty("integrasjon.tilgang.azp", "azp")
        // PDL
        System.setProperty("integrasjon.pdl.url", "http://localhost:${pdl.port()}")
        System.setProperty("integrasjon.pdl.scope", "scope")
        // NORG
        System.setProperty("integrasjon.norg.url", "http://localhost:${norg.port()}")
    }

    override fun close() {
        azure.stop()
        tilgang.stop()
        pdl.stop()
        norg.stop()
    }

}