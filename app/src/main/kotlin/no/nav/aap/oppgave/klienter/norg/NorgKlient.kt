package no.nav.aap.oppgave.klienter.norg

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import org.slf4j.LoggerFactory
import java.net.URI

data class Enhet(val enhetNr: String)

data class EnhetMedNavn(val enhetNr: String, val navn: String)

data class FinnNavenhetRequest(
    val geografiskOmraade: String?,
    val skjermet: Boolean = false,
    val diskresjonskode : Diskresjonskode,
    val tema: String = "AAP",
    val behandlingstema: String = "ab0014"
)

enum class Diskresjonskode { SPFO, SPSF, ANY }

class NorgKlient {

    private val log = LoggerFactory.getLogger(NorgKlient::class.java)

    private val url = URI.create(requiredConfigForKey("integrasjon.norg.url"))
    private val config = ClientConfig()
    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = NoTokenTokenProvider(),
        prometheus = prometheus
    )

    fun finnEnhet(geografiskTilknyttning: String?, erNavansatt: Boolean, diskresjonskode: Diskresjonskode): String {
        log.info("Finner enhet for $geografiskTilknyttning")
        val finnEnhetUrl = url.resolve("norg2/api/v1/arbeidsfordeling/enheter/bestmatch")
        val request = PostRequest(
            FinnNavenhetRequest(geografiskTilknyttning, erNavansatt, diskresjonskode)
        )
        val enheter = client.post<FinnNavenhetRequest, List<Enhet>>(finnEnhetUrl, request)
        if (enheter == null) {
            error("Feil i response fra norg for geo = $geografiskTilknyttning, erNavansatt = $erNavansatt, diskresjonskode = $diskresjonskode")
        }
        if (enheter.isEmpty()) {
            log.warn("Fant ingen enhet for geografiskTilknyttning=$geografiskTilknyttning, erNavansatt=$erNavansatt, diskresjonskode=$diskresjonskode. Returnerer UDEFINERT")
            return "UDEFINERT"
        }
        if (enheter.size > 1) {
            log.warn("Flere aktuelle enheter for geografiskTilknyttning=$geografiskTilknyttning, erNavansatt=$erNavansatt, diskresjonskode=$diskresjonskode. Returnerer første.")
        }
        return enheter.first().enhetNr
    }

    fun hentEnheter(): Map<String, String> {
        log.info("Henter enheter")
        val hentEnheterUrl = url.resolve("norg2/api/v1/enhet/simple")
        val enheter = client.get<List<EnhetMedNavn>>(hentEnheterUrl, GetRequest())
        if (enheter == null) {
            return mapOf()
        }
        return enheter.associate { it.enhetNr to it.navn }
    }

}