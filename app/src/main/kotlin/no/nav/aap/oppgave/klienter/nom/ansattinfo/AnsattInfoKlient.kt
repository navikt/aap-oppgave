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

interface AnsattInfoKlient {
    fun hentAnsattNavnHvisFinnes(navIdent: String) : String?
    fun søkEtterSaksbehandler(søketekst: String): List<AnsattFraSøk>
}

class NomApiKlient(
    private val restClient: RestClient<InputStream>,
): AnsattInfoKlient {
    private val log = LoggerFactory.getLogger(NomApiKlient::class.java)
    private val graphqlUrl = URI.create(requiredConfigForKey("integrasjon.nom.api.url"))

    init {
        CaffeineCacheMetrics.monitor(prometheus, saksbehandlerNavnCache, "nom_ansatt")
    }

    companion object {
        private val saksbehandlerNavnCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofDays(1))
            .recordStats()
            .build<String, String>()

        private fun getClientConfig() = ClientConfig(
            scope = requiredConfigForKey("integrasjon.nom.api.scope"),
        )

        fun withClientCredentialsRestClient() =
            NomApiKlient(
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

    override fun søkEtterSaksbehandler(søketekst: String): List<AnsattFraSøk> {
        val request = AnsattSøkRequest(søkQuery, AnsattSøkVariables(søketekst))
        val response = ansattSøkQuery(request)
        val responseData = checkNotNull(response.data) {
            "Kunne ikke søke etter ansatte i NOM. Feilmelding: ${response.errors}"
        }
        return requireNotNull(responseData.search) { "Søkeinfo fra NOM kan ikke være null" }
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

    private fun ansattSøkQuery(request: AnsattSøkRequest): AnsattSøkResponse {
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

private const val soketekst = "\$soketekst"
val søkQuery = """
    query($soketekst: String!) {
  search(term: $soketekst) {
    ... on Ressurs {
      visningsnavn
      navident
      orgTilknytning {
        orgEnhet {
          orgEnhetsType
        }
      }
    }
  }
}
""".trimIndent()