package no.nav.aap.oppgave.klienter.nom.ansattinfo

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.oppgave.metrikker.prometheus
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.time.Duration
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import java.time.LocalDate

interface AnsattInfoGateway {
    fun hentAnsattNavnHvisFinnes(navIdent: String) : String?
    fun hentSluttetDatoHvisAnsattHarSluttet(navIdent: String): LocalDate?
    fun hentDeaktiverteBrukere(navIdenter: List<String>): List<String>
}

class NomApiGateway
private constructor(
    private val restClient: RestClient<InputStream>,
): AnsattInfoGateway {
    private val log = LoggerFactory.getLogger(NomApiGateway::class.java)
    private val graphqlUrl = URI.create(requiredConfigForKey("INTEGRASJON_NOM_API_URL"))

    companion object {
        private val saksbehandlerNavnCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofDays(1))
            .recordStats()
            .build<String, String>()

        private fun getClientConfig() = ClientConfig(
            scope = requiredConfigForKey("INTEGRASJON_NOM_API_SCOPE"),
        )

        init {
            CaffeineCacheMetrics.monitor(prometheus, saksbehandlerNavnCache, "nom_ansatt")
        }

        fun withClientCredentialsRestClient() =
            NomApiGateway(
                RestClient(
                    config = getClientConfig(),
                    tokenProvider = AzureM2MTokenProvider,
                    responseHandler = NomApiResponseHandler(),
                    prometheus = prometheus
                )
            )

    }

    override fun hentAnsattNavnHvisFinnes(navIdent: String): String? {
        return try {
            hentAnsattNavn(navIdent)
        } catch (e: Exception) {
            log.warn("Feil ved henting av navn for ansatt $navIdent. Fortsetter uten.", e)
            null
        }
    }

    private fun hentAnsattNavn(navIdent: String): String =
        saksbehandlerNavnCache.get(navIdent) {
            val request = AnsattInfoRequest(navnQuery, AnsattInfoVariables(navIdent))
            val response = checkNotNull(ansattInfoQuery(request).data?.ressurs) {
                "Fant ikke ansatt i NOM"
            }
            response.visningsnavn
        }


    private fun ansattInfoQuery(request: AnsattInfoRequest): AnsattInfoRespons {
        val httpRequest = PostRequest(body = request)
        return requireNotNull(restClient.post(uri = graphqlUrl, request = httpRequest))
    }

    override fun hentSluttetDatoHvisAnsattHarSluttet(navIdent: String): LocalDate? {
        val request = AnsattInfoRequest(sluttetQuery, AnsattInfoVariables(navIdent))
        val response = checkNotNull(ansattInfoQuery(request).data?.ressurs) {
            "Fant ikke ansatt i NOM"
        }
        return response.sluttdato?.let { LocalDate.parse(it) }
    }

    override fun hentDeaktiverteBrukere(navIdenter: List<String>): List<String> {
        return  navIdenter.filter { navIdent ->
             hentSluttetDatoHvisAnsattHarSluttet(navIdent)?.isBefore(LocalDate.now()) == true
        }
    }
}

private const val navIdent = "\$navIdent"
val navnQuery = """
    query($navIdent: String!) {
      ressurs(where: {navident: $navIdent}) {
        visningsnavn
      }
    }
""".trimIndent()

val sluttetQuery = """
    query($navIdent: String!) {
      ressurs(where: {navident: $navIdent}) {
        visningsnavn,
        sluttdato
      }
    }
""".trimIndent()
