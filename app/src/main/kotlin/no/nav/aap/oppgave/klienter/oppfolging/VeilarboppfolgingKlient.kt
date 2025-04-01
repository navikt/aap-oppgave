package no.nav.aap.oppgave.klienter.oppfolging

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI

private data class HentVeilederRequest(
    val fnr: String
)

private data class HentVeilederResponse(
    val veilederIdent: String
)

interface IVeilarbarboppfolgingKlient {
    fun hentVeileder(personIdent: String): String?
}

class VeilarbarboppfolgingKlient: IVeilarbarboppfolgingKlient {

    private val url = URI.create(requiredConfigForKey("integrasjon.veilarboppfolging.url"))

    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.veilarboppfolging.scope"),
    )

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus
    )

    /**
     * Kildekode for endepunktet: https://github.com/navikt/veilarboppfolging/blob/main/src/main/java/no/nav/veilarboppfolging/controller/v3/VeilederV3Controller.java
     * Per 28-03-25
     */
    override fun hentVeileder(personIdent: String): String? {
        val hentVeilederUrl = url.resolve("/veilarboppfolging/api/v3/hent-veileder")
        val request = PostRequest(
            body = HentVeilederRequest(personIdent),
            additionalHeaders = listOf(
                Header("Nav-Consumer-Id", "aap-oppgave"),
            )
        )
        val resp = client.post<HentVeilederRequest, HentVeilederResponse?>(hentVeilederUrl, request)

        return resp?.veilederIdent
    }

}