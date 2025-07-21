package no.nav.aap.oppgave.fakes

import io.getunleash.FakeUnleash
import no.nav.aap.oppgave.unleash.UnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

data class FakesConfig(
    var negativtSvarFraTilgangForBehandling: Set<UUID> = setOf()
)

class Fakes(val fakesConfig: FakesConfig = FakesConfig()) : AutoCloseable, ParameterResolver,
    AfterAllCallback, BeforeAllCallback {

    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val azure = FakeServer(module = { azureFake() })
    private val tilgang = FakeServer(module = { tilgangFake(fakesConfig) })
    private val pdl = FakeServer(module = { pdlFake() })
    private val norg = FakeServer(module = { norgFake() })
    private val nom = FakeServer(module = { nomFake() })
    private val veilarbarena = FakeServer(module = { veilarbarenaFake() })
    private val veilarboppfolging = FakeServer(module = { veilarboppfolgingFake() })
    private val sykefravavaroppfolging = FakeServer(module = { sykefravaroppfolgingFake() })
    private val msGraph = FakeServer(module = { msGraphFake() })
    private val statistikkFake = FakeServer(module = { statistikkFake() })
    private val fakeServere = listOf(
        azure,
        tilgang,
        pdl,
        norg,
        nom,
        veilarbarena,
        veilarboppfolging,
        sykefravavaroppfolging,
        msGraph,
        statistikkFake
    )

    override fun close() {
        fakeServere.forEach {
            it.stop()
        }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?
    ): Boolean {
        // FakesConfig
        if (parameterContext?.parameter?.type == FakesConfig::class.java) {
            return true
        }
        return false
    }

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?
    ): Any? {
        return fakesConfig
    }

    override fun afterAll(context: ExtensionContext?) {
        close()
    }

    override fun beforeAll(context: ExtensionContext?) {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
        // Unleash
        UnleashServiceProvider.setUnleashService(
            UnleashService(FakeUnleash().apply {
                enableAll()
            })
        )

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
        // NOM
        System.setProperty("integrasjon.nom.url", "http://localhost:${nom.port()}")
        System.setProperty("integrasjon.nom.scope", "scope")
        // MS GRAPH
        System.setProperty("MS_GRAPH_BASE_URL", "http://localhost:${msGraph.port()}")
        System.setProperty("MS_GRAPH_SCOPE", "scope")
        // Veilarbarena
        System.setProperty("integrasjon.veilarbarena.url", "http://localhost:${veilarbarena.port()}")
        System.setProperty("integrasjon.veilarbarena.scope", "scope")
        // Veilarboppfolging
        System.setProperty("integrasjon.veilarboppfolging.url", "http://localhost:${veilarboppfolging.port()}")
        System.setProperty("integrasjon.veilarboppfolging.scope", "scope")
        // Sykefraværoppfølging
        System.setProperty("integrasjon.syfo.url", "http://localhost:${sykefravavaroppfolging.port()}")
        System.setProperty("integrasjon.syfo.scope", "scope")

        System.setProperty("AAP_SAKSBEHANDLER_NASJONAL", "saksbehandler-rolle")
        System.setProperty("AAP_SAKSBEHANDLER_OPPFOLGING", "veileder-rolle")
        System.setProperty("AAP_KVALITETSSIKRER", "kvalitetssikrer-rolle")
        System.setProperty("AAP_BESLUTTER", "beslutter-rolle")

        System.setProperty("integrasjon.statistikk.url", "http://localhost:${statistikkFake.port()}")
        System.setProperty("integrasjon.statistikk.scope", "scope")
    }

}