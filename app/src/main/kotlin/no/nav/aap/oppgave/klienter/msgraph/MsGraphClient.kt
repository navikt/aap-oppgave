package no.nav.aap.oppgave.klienter.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.komponenter.miljo.Miljø
import java.net.URI
import java.util.*
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.tokenx.OnBehalfOfTokenProvider as TexasOnBehalfOfTokenProvider

interface IMsGraphClient {
    fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf
    fun hentFortroligAdresseGruppe(currentToken: String): MemberOf
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
        tokenProvider = if (Miljø.erProd())
            OnBehalfOfTokenProvider
        else
            TexasOnBehalfOfTokenProvider(identityProvider = "azuread"),
        prometheus = prometheus,
    )

    override fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf {
        val url =
            baseUrl.resolve("me/memberOf?\$count=true&\$top=999&\$filter=${starterMedFilter(ENHET_GROUP_PREFIX)}")
        val respons = httpClient.get<MemberOf>(
            url,
            GetRequest(
                currentToken = OidcToken(currentToken),
                additionalHeaders = listOf(Header("ConsistencyLevel", "eventual"))
            )
        ) ?: MemberOf()
        return respons
    }

    override fun hentFortroligAdresseGruppe(currentToken: String): MemberOf {
        val url =
            baseUrl.resolve("me/memberOf?\$count=true&\$top=1&\$filter=${starterMedFilter(FORTROLIG_ADRESSE_GROUP)}")
        val respons = httpClient.get<MemberOf>(
            url,
            GetRequest(
                currentToken = OidcToken(currentToken),
                additionalHeaders = listOf(Header("ConsistencyLevel", "eventual"))
            )
        ) ?: MemberOf()
        return respons
    }

    private fun starterMedFilter(prefix: String): String {
        return "startswith(displayName,\'$prefix\')"
    }

    companion object {
        const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
        const val FORTROLIG_ADRESSE_GROUP = "0000-GA-Fortrolig_Adresse"
    }
}

data class MemberOf(
    @param:JsonProperty("value")
    val groups: List<Group> = emptyList()
)

data class Group(
    @param:JsonProperty("id")
    val id: UUID,
    @param:JsonProperty("mailNickname")
    val name: String
)

