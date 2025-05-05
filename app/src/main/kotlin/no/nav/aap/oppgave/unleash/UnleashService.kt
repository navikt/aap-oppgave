package no.nav.aap.oppgave.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import no.nav.aap.komponenter.config.requiredConfigForKey

interface IUnleashService {
    fun isEnabled(featureToggle: FeatureToggle): Boolean
}

class UnleashService (private val unleash: Unleash) : IUnleashService {
    override fun isEnabled(featureToggle: FeatureToggle): Boolean = unleash.isEnabled(featureToggle.key())
}

/**
 * Provider for UnleashService. The biggest advantage of using the provider instead is that we can use a singleton for
 * UnleashService, which we can overwrite with a fake in tests and when we run the application localy.
 */
object UnleashServiceProvider {
    private var unleashService: IUnleashService? = null

    fun provideUnleashService(): IUnleashService {
        if (unleashService == null) {
            unleashService = UnleashService(
                DefaultUnleash(
                    UnleashConfig.builder()
                        .appName(requiredConfigForKey("nais.app.name"))
                        .unleashAPI("${requiredConfigForKey("unleash.server.api.url")}/api")
                        .apiKey(requiredConfigForKey("unleash.server.api.token"))
                        .build()
                )
            )
        }
        return unleashService!!
    }

    fun setUnleashService(service: UnleashService) {
        unleashService = service
    }
}