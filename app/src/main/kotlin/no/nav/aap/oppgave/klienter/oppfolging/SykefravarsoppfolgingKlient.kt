package no.nav.aap.oppgave.klienter.oppfolging

import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI
import java.time.Duration

private data class HentVeilederSykefravarsoppfolgingResponse(
    val tildeltVeilederident: String?,
)

interface ISykefravarsoppfolgingKlient {
    fun hentVeileder(personIdent: String): String?
}

object SykefravarsoppfolgingKlient: ISykefravarsoppfolgingKlient {
    private val cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofHours(4))
        .build<String, HentVeilederSykefravarsoppfolgingResponse>()

    private val url = URI.create(requiredConfigForKey("integrasjon.syfo.url"))

    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.syfo.scope"),
    )

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus
    )

    /**
     * Kildekode for endepunktet: https://github.com/navikt/syfooversiktsrv/blob/master/src/main/kotlin/no/nav/syfo/personstatus/api/v2/endpoints/PersonTildelingSystemApi.kt
     * Per 28-05-25
     */
    override fun hentVeileder(personIdent: String): String? {
        val cachetRespons = cache.getIfPresent(personIdent)
        if (cachetRespons != null) return cachetRespons.tildeltVeilederident

        val hentVeilederUrl = url.resolve("/api/v1/system/persontildeling/personer/single")
        val request = GetRequest(
            additionalHeaders = listOf(
                Header("nav-personident", personIdent),
                Header("Nav-Consumer-Id", "aap-oppgave"),
            )
        )
        val resp = client.get<HentVeilederSykefravarsoppfolgingResponse?>(hentVeilederUrl, request)

        cache.put(personIdent, resp ?: HentVeilederSykefravarsoppfolgingResponse(null))
        return resp?.tildeltVeilederident?.takeUnless(String::isBlank)
    }

}