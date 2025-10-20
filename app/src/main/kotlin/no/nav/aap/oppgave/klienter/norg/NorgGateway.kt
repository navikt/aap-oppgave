package no.nav.aap.oppgave.klienter.norg

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

data class Enhet(val enhetNr: String)

data class EnhetMedNavn(val enhetNr: String, val navn: String)

data class FinnNavenhetRequest(
    val geografiskOmraade: String?,
    val skjermet: Boolean = false,
    val diskresjonskode : Diskresjonskode,
    val tema: String = "AAP",
    val behandlingstema: String = "ab0014"
)

enum class Diskresjonskode(val prioritet: Int) { ANY(0), SPFO(1), SPSF(2) }

interface INorgGateway {
    fun finnEnhet(geografiskTilknyttning: String?, erNavansatt: Boolean, diskresjonskode: Diskresjonskode): String
    fun hentEnheter(): Map<String, String>
    fun hentOverordnetFylkesenheter(enhetsnummer: String): List<String>
}

class NorgGateway: INorgGateway {

    private val log = LoggerFactory.getLogger(NorgGateway::class.java)

    private val url = URI.create(requiredConfigForKey("integrasjon.norg.url"))
    private val config = ClientConfig()
    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = NoTokenTokenProvider(),
        prometheus = prometheus
    )

    override fun finnEnhet(
        geografiskTilknyttning: String?,
        erNavansatt: Boolean,
        diskresjonskode: Diskresjonskode
    ): String {
        log.info("Finner enhet for $geografiskTilknyttning")
        val finnEnhetUrl = url.resolve("norg2/api/v1/arbeidsfordeling/enheter/bestmatch")
        val request = PostRequest(
            FinnNavenhetRequest(geografiskTilknyttning, erNavansatt, diskresjonskode)
        )
        val enheter = client.post<FinnNavenhetRequest, List<Enhet>>(finnEnhetUrl, request)

        requireNotNull(enheter) {
            "Feil i response fra norg for geo = $geografiskTilknyttning, erNavansatt = $erNavansatt, diskresjonskode = $diskresjonskode"
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

    override fun hentEnheter(): Map<String, String> = enheterCache.get(ENHETER_CACHE_KEY) {
        log.info("Henter enheter")

        val hentEnheterUrl = url.resolve("norg2/api/v1/enhet/simple")
        val enheter = client.get<List<EnhetMedNavn>>(hentEnheterUrl, GetRequest())

        requireNotNull(enheter) {
            "Fikk tom respons fra Norg2"
        }

        enheter.associate { it.enhetNr to it.navn }
    }

    override fun hentOverordnetFylkesenheter(enhetsnummer: String): List<String> = fylkesenheterCache.get(enhetsnummer) {
        log.info("Henter overordnet fylkesenhet for $enhetsnummer")
        val hentOverordnetFylkesenhetUrl =
            url.resolve("norg2/api/v1/enhet/$enhetsnummer/overordnet?organiseringsType=FYLKE")
        val enheter = checkNotNull(
            client.get<List<EnhetMedNavn>>(
                hentOverordnetFylkesenhetUrl, GetRequest(
                    additionalHeaders = listOf(
                        Header("Content-Type", "application/json")
                    )
                )
            )
        )

        enheter.map { it.enhetNr }
    }

    companion object {
        private const val ENHETER_CACHE_KEY = "NORG2_ALLE_ENHETER"

        private val enheterCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats()
            .build<String, Map<String, String>>()

        private val fylkesenheterCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(6))
            .recordStats()
            .build<String, List<String>>()

        init {
            CaffeineCacheMetrics.monitor(prometheus, enheterCache, "norg2_enheter")
            CaffeineCacheMetrics.monitor(prometheus, fylkesenheterCache, "norg2_fylkesenheter")
        }
    }
}