package no.nav.aap.oppgave.klienter.arena

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import org.slf4j.LoggerFactory
import java.net.URI

private data class HentOppfølgingsenhetRequest(
    val fnr: String
)

private data class HentOppfølgingsenhetResponse(
    val oppfolgingsenhet: String?
)

class VeilarbarenaClient {

    private val log = LoggerFactory.getLogger(VeilarbarenaClient::class.java)

    private val url = URI.create(requiredConfigForKey("integrasjon.veilarbarena.url"))

    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.veilarbarena.scope"),
    )

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider
    )

    fun hentOppfølgingsenhet(personIdent: String): String? {
        log.info("Finner oppfølgingsenhet for fnr: $personIdent")
        val hentStatusUrl = url.resolve("/api/v2/arena/hent-status")
        val request = PostRequest(
            body = HentOppfølgingsenhetRequest(personIdent),
            additionalHeaders = listOf(Header("forceSync", "true"))
        )
        val resp = client.post<HentOppfølgingsenhetRequest, HentOppfølgingsenhetResponse?>(hentStatusUrl, request)
        return resp?.oppfolgingsenhet
    }

}