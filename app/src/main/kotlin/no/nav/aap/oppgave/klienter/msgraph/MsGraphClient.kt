package no.nav.aap.oppgave.klienter.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import java.net.URI
import java.util.*

interface IMsGraphClient {
    fun hentAdGrupper(currentToken: String, ident: String): MemberOf
    fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf
}

class MsGraphClient(
    prometheus: PrometheusMeterRegistry
) : IMsGraphClient {
    private val baseUrl = URI.create(requiredConfigForKey("ms.graph.base.url"))

    private val clientConfig = ClientConfig(
        scope = requiredConfigForKey("ms.graph.scope")
    )
    private val httpClient = RestClient.withDefaultResponseHandler(
        config = clientConfig,
        tokenProvider = OnBehalfOfTokenProvider,
        prometheus = prometheus,
    )

    // NB: Denne har ikke implementert paginering, og antall roller kan fort overstige default page size
    override fun hentAdGrupper(currentToken: String, ident: String): MemberOf {
        val url = baseUrl.resolve("me/memberOf")
        val respons = httpClient.get<MemberOf>(url, GetRequest(currentToken = OidcToken(currentToken))) ?: MemberOf()
        return respons
    }

    override fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf {
        val url =
            baseUrl.resolve("me/memberOf?\$count=true&\$top=999&\$filter=${starterMedFilter(ENHET_GROUP_PREFIX)}")
        val respons = httpClient.get<MemberOf>(url, GetRequest(currentToken = OidcToken(currentToken))) ?: MemberOf()
        return respons
    }

    private fun starterMedFilter(prefix: String): String {
        return "startswith(displayName,\'$prefix\')"
    }

    companion object {
        private const val MSGRAPH_PREFIX = "msgraph"
        const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
    }
}

data class MemberOf(
    @JsonProperty("value")
    val groups: List<Group> = emptyList()
)

data class Group(
    @JsonProperty("id")
    val id: UUID,
    @JsonProperty("mailNickname")
    val name: String
)

