package no.nav.aap.oppgave.klienter.oppfolging

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI


private data class HentVeilederSykefravarsoppfolgingResponse(
    val personident: String,
    val tildeltVeilederident: String?,
    val tildeltEnhet: String?,
)

interface ISykefravarsoppfolgingKlient {
    fun hentVeileder(personIdent: String): String?
}

class SykefravarsoppfolgingKlient: ISykefravarsoppfolgingKlient {

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
        val hentVeilederUrl = url.resolve("/api/v1/system/persontildeling/personer/single")
        val request = GetRequest(
            additionalHeaders = listOf(
                Header("nav-personident", personIdent),
                Header("Nav-Consumer-Id", "aap-oppgave"),
            )
        )
        val resp = client.get<HentVeilederSykefravarsoppfolgingResponse?>(hentVeilederUrl, request)

        return resp?.tildeltVeilederident
    }

}