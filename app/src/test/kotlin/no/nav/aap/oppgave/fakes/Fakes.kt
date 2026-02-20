package no.nav.aap.oppgave.fakes

import io.getunleash.FakeUnleash
import no.nav.aap.oppgave.unleash.UnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

data class FakesConfig(
    var negativtSvarFraTilgangForBehandling: Set<UUID> = setOf(),
    var relaterteIdenterPåBehandling: List<String> = emptyList(),
)

class Fakes(val fakesConfig: FakesConfig = FakesConfig()) : AutoCloseable, ParameterResolver,
    BeforeAllCallback {

    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val azure = FakeServer(module = { azureFake() })
    private val tilgang = FakeServer(module = { tilgangFake(fakesConfig) })
    private val behandlingsflyt = FakeServer(module = { behandlingsflytFake(fakesConfig) })
    private val pdl = FakeServer(module = { pdlFake() })
    private val norg = FakeServer(module = { norgFake() })
    private val nomSkjerming = FakeServer(module = { nomSkjermingFake() })
    private val nomAnsattInfo = FakeServer(module = { nomAnsattInfoFake() })
    private val veilarbarena = FakeServer(module = { veilarbarenaFake() })
    private val veilarboppfolging = FakeServer(module = { veilarboppfolgingFake() })
    private val sykefravavaroppfolging = FakeServer(module = { sykefravaroppfolgingFake() })
    private val msGraph = FakeServer(module = { msGraphFake() })
    private val statistikkFake = FakeServer(module = { statistikkFake() })
    private val fakeServere = listOf(
        azure,
        tilgang,
        behandlingsflyt,
        pdl,
        norg,
        nomSkjerming,
        nomAnsattInfo,
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
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        // FakesConfig
        return parameterContext.parameter.type == FakesConfig::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        return fakesConfig
    }

    override fun beforeAll(context: ExtensionContext) {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
        setProperties()
    }

    fun setProperties() {
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
        // Nais
        System.setProperty("nais.token.exchange.endpoint", "http://localhost:${azure.port()}/token")
        // PDL
        System.setProperty("integrasjon.pdl.url", "http://localhost:${pdl.port()}")
        System.setProperty("integrasjon.pdl.scope", "scope")
        // NORG
        System.setProperty("integrasjon.norg.url", "http://localhost:${norg.port()}")
        // NOM (skjerming)
        System.setProperty("integrasjon.nom.url", "http://localhost:${nomSkjerming.port()}")
        System.setProperty("integrasjon.nom.scope", "scope")
        // NOM (api)
        System.setProperty("integrasjon.nom.api.url", "http://localhost:${nomAnsattInfo.port()}")
        System.setProperty("integrasjon.nom.api.scope", "scope")
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
        // Behandlingsflyt
        if (System.getProperty("INTEGRASJON_BEHANDLINGSFLYT_URL").isNullOrEmpty()) {
            System.setProperty("integrasjon.behandlingsflyt.url", "http://localhost:${behandlingsflyt.port()}")
        }
        System.setProperty("integrasjon.behandlingsflyt.scope", "scope")
        // Statistikk
        System.setProperty("integrasjon.statistikk.url", "http://localhost:${statistikkFake.port()}")
        System.setProperty("integrasjon.statistikk.scope", "scope")
        // Roller
        System.setProperty("AAP_SAKSBEHANDLER_NASJONAL", "saksbehandler-rolle")
        System.setProperty("AAP_SAKSBEHANDLER_OPPFOLGING", "veileder-rolle")
        System.setProperty("AAP_KVALITETSSIKRER", "kvalitetssikrer-rolle")
        System.setProperty("AAP_BESLUTTER", "beslutter-rolle")
    }

}