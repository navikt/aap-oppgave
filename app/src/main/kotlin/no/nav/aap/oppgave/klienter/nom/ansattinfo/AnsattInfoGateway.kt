package no.nav.aap.oppgave.klienter.nom.ansattinfo

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.time.Duration

interface AnsattInfoGateway {
    fun hentAnsattNavnHvisFinnes(navIdent: String) : String?
}

class NomApiGateway(
    private val restClient: RestClient<InputStream>,
): AnsattInfoGateway {
    private val log = LoggerFactory.getLogger(NomApiGateway::class.java)
    private val graphqlUrl = URI.create(requiredConfigForKey("integrasjon.nom.api.url"))

    companion object {
        private val saksbehandlerNavnCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofDays(1))
            .recordStats()
            .build<String, String>()

        private fun getClientConfig() = ClientConfig(
            scope = requiredConfigForKey("integrasjon.nom.api.scope"),
        )

        init {
            CaffeineCacheMetrics.monitor(prometheus, saksbehandlerNavnCache, "nom_ansatt")
        }

        fun withClientCredentialsRestClient() =
            NomApiGateway(
                RestClient(
                    config = getClientConfig(),
                    tokenProvider = ClientCredentialsTokenProvider,
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
}

private const val navIdent = "\$navIdent"
val navnQuery = """
    query($navIdent: String!) {
      ressurs(where: {navident: $navIdent}) {
        visningsnavn
      }
    }
""".trimIndent()