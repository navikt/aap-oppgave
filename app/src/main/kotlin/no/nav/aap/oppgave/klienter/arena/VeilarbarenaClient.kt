package no.nav.aap.oppgave.klienter.arena

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.IkkeFunnetException
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI

private data class HentOppfølgingsenhetRequest(
    val fnr: String
)

private data class HentOppfølgingsenhetResponse(
    val oppfolgingsenhet: String?
)

interface IVeilarbarenaClient {
    fun hentOppfølgingsenhet(personIdent: String): String?
}

class VeilarbarenaClient: IVeilarbarenaClient {

    private val url = URI.create(requiredConfigForKey("integrasjon.veilarbarena.url"))

    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.veilarbarena.scope"),
    )

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus
    )

    override fun hentOppfølgingsenhet(personIdent: String): String? {
        val hentStatusUrl = url.resolve("/veilarbarena/api/v2/arena/hent-status")
        val request = PostRequest(
            body = HentOppfølgingsenhetRequest(personIdent),
            additionalHeaders = listOf(
                Header("forceSync", "true"),
                Header("Nav-Consumer-Id", "aap-oppgave"),
            )
        )
        val resp = try {
            client.post<HentOppfølgingsenhetRequest, HentOppfølgingsenhetResponse?>(hentStatusUrl, request)
        } catch (_: IkkeFunnetException) {
            // Tjenesten returner 404 dersom det ikke finnes noen oppfølgingsenhet for oppgitt fnr
            return null
        }
        return resp?.oppfolgingsenhet
    }

}