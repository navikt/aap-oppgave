package no.nav.aap.oppgave.klienter.nom.skjerming

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI
import java.time.Duration

private data class EgenansattRequest(val personident: String)

interface SkjermingGateway {
    fun erSkjermet(ident: String): Boolean
}

class NomSkjermingGateway : SkjermingGateway {
    private val url = URI.create(requiredConfigForKey("integrasjon.nom.url"))

    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.nom.scope"),
    )

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus
    )

    init {
        CaffeineCacheMetrics.monitor(prometheus, skjermingCache, "nom_skjermet")
    }

    override fun erSkjermet(ident: String): Boolean =
        skjermingCache.get(ident) {
            val egenansattUrl = url.resolve("/skjermet")
            val request = PostRequest(
                body = EgenansattRequest(ident),
            )

            client.post<EgenansattRequest, Boolean>(egenansattUrl, request)
                ?: throw SkjermingException("Kunne ikke hente skjermingstatus for ident")
        }

    companion object {
        private val skjermingCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(4))
            .recordStats()
            .build<String, Boolean>()

    }

}
