package no.nav.aap.oppgave.klienter.norg

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import org.slf4j.LoggerFactory
import java.net.URI

data class Enhet(val enhetNr: String)

data class FinnNavenhetRequest(
    val geografiskOmraade: String?,
    val skjermet: Boolean = false,
    val diskresjonskode : Diskresjonskode
){
    val tema = "AAP"
}

enum class Diskresjonskode { SPFO, SPSF, ANY }

class NorgKlient {

    private val log = LoggerFactory.getLogger(NorgKlient::class.java)

    private val url = URI.create(requiredConfigForKey("integrasjon.norg.url"))
    private val config = ClientConfig()
    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = NoTokenTokenProvider(),
    )

    fun finnEnhet(geografiskTilknyttning: String?, erNavansatt: Boolean, diskresjonskode: Diskresjonskode): String {
        log.info("Finner enhet for $geografiskTilknyttning")
        val finnEnhetUrl = url.resolve("norg2/api/v1/arbeidsfordeling/enheter/bestmatch")
        val request = PostRequest(
            FinnNavenhetRequest(geografiskTilknyttning, erNavansatt, diskresjonskode)
        )

        return client.post<FinnNavenhetRequest, List<Enhet>>(finnEnhetUrl, request)
            .let { response -> response?.first()?.enhetNr } ?: error("Feil i response fra norg")
    }

}