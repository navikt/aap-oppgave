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

class Fakes(val fakesConfig: FakesConfig = FakesConfig()) : AutoCloseable, ParameterResolver, BeforeAllCallback {

    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val texas = FakeServer(module = { texasFake() })
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
        texas,
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
        // Ikke prod-miljø-variabler
        System.setProperty("NAIS_CLUSTER_NAME", "LOCAL")

        // Texas
        System.setProperty("NAIS_TOKEN_ENDPOINT", "http://localhost:${texas.port()}/token")
        System.setProperty("NAIS_TOKEN_EXCHANGE_ENDPOINT", "http://localhost:${texas.port()}/token/exchange")
        System.setProperty("NAIS_TOKEN_INTROSPECTION_ENDPOINT", "http://localhost:${texas.port()}/introspect")
        // Tilgang
        System.setProperty("INTEGRASJON_TILGANG_URL", "http://localhost:${tilgang.port()}")
        System.setProperty("INTEGRASJON_TILGANG_SCOPE", "scope")
        System.setProperty("INTEGRASJON_TILGANG_AZP", "azp")
        // PDL
        System.setProperty("INTEGRASJON_PDL_URL", "http://localhost:${pdl.port()}")
        System.setProperty("INTEGRASJON_PDL_SCOPE", "scope")
        // NORG
        System.setProperty("INTEGRASJON_NORG_URL", "http://localhost:${norg.port()}")
        // NOM (skjerming)
        System.setProperty("INTEGRASJON_NOM_URL", "http://localhost:${nomSkjerming.port()}")
        System.setProperty("INTEGRASJON_NOM_SCOPE", "scope")
        // NOM (api)
        System.setProperty("INTEGRASJON_NOM_API_URL", "http://localhost:${nomAnsattInfo.port()}")
        System.setProperty("INTEGRASJON_NOM_API_SCOPE", "scope")
        // MS GRAPH
        System.setProperty("MS_GRAPH_BASE_URL", "http://localhost:${msGraph.port()}")
        System.setProperty("MS_GRAPH_SCOPE", "scope")
        // Veilarbarena
        System.setProperty("INTEGRASJON_VEILARBARENA_URL", "http://localhost:${veilarbarena.port()}")
        System.setProperty("INTEGRASJON_VEILARBARENA_SCOPE", "scope")
        // Veilarboppfolging
        System.setProperty("INTEGRASJON_VEILARBOPPFOLGING_URL", "http://localhost:${veilarboppfolging.port()}")
        System.setProperty("INTEGRASJON_VEILARBOPPFOLGING_SCOPE", "scope")
        // Sykefraværoppfølging
        System.setProperty("INTEGRASJON_SYFO_URL", "http://localhost:${sykefravavaroppfolging.port()}")
        System.setProperty("INTEGRASJON_SYFO_SCOPE", "scope")
        // Behandlingsflyt
        if (System.getenv("INTEGRASJON_BEHANDLINGSFLYT_URL").isNullOrEmpty()) {
            System.setProperty("INTEGRASJON_BEHANDLINGSFLYT_URL", "http://localhost:${behandlingsflyt.port()}")
        }
        System.setProperty("INTEGRASJON_BEHANDLINGSFLYT_SCOPE", "scope")
        // Statistikk
        System.setProperty("INTEGRASJON_STATISTIKK_URL", "http://localhost:${statistikkFake.port()}")
        System.setProperty("INTEGRASJON_STATISTIKK_SCOPE", "scope")
        // Roller
        System.setProperty("AAP_SAKSBEHANDLER_NASJONAL", "saksbehandler-rolle")
        System.setProperty("AAP_SAKSBEHANDLER_OPPFOLGING", "veileder-rolle")
        System.setProperty("AAP_KVALITETSSIKRER", "kvalitetssikrer-rolle")
        System.setProperty("AAP_BESLUTTER", "beslutter-rolle")

        // AZP-UUID-der
        System.setProperty("AZP_API_INTERN", UUID.randomUUID().toString())
    }

}