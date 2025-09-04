package no.nav.aap.oppgave.klienter.nom.ansattinfo

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI

interface AnsattInfoKlient {
    fun hentAnsattNavnHvisFinnes(navIdent: String) : String?
}

class NomApiKlient(
    private val restClient: RestClient<InputStream>,
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService(),
): AnsattInfoKlient {
    private val log = LoggerFactory.getLogger(NomApiKlient::class.java)
    private val graphqlUrl = URI.create(requiredConfigForKey("integrasjon.nom.api.url"))

    companion object {
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
                ),
                unleashService = UnleashServiceProvider.provideUnleashService()
            )

    }

    override fun hentAnsattNavnHvisFinnes(navIdent: String): String? {
        return if (unleashService.isEnabled(FeatureToggles.HentSaksbehandlerNavnFraNom)) {
            try {
                hentAnsattNavn(navIdent)
            } catch (e: Exception) {
                log.warn("Feil ved henting av navn for ansatt $navIdent. Fortsetter uten.", e)
                null
            }
        } else {
            null
        }
    }

    private fun hentAnsattNavn(navIdent: String): String {
        val request = AnsattInfoRequest(navnQuery, AnsattInfoVariables(navIdent))
        val response = checkNotNull(query(request).data?.ressurs) {
            "Fant ikke ansatt i NOM"
        }
        return response.visningsnavn
    }


    private fun query(request: AnsattInfoRequest): AnsattInfoRespons {
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