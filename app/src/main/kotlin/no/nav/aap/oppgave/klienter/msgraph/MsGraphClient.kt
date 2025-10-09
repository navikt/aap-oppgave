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
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.komponenter.miljo.Miljø
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.tokenx.OnBehalfOfTokenProvider as TexasOnBehalfOfTokenProvider

interface IMsGraphClient {
    fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf
    fun hentFortroligAdresseGruppe(currentToken: String): MemberOf
    fun hentMedlemmerIGruppe(enhetsnummer: String): GroupMembers
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

    private val httpClientM2m = RestClient.withDefaultResponseHandler(
        config = clientConfig,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus,
    )

    private val log = LoggerFactory.getLogger(MsGraphClient::class.java)

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

    override fun hentMedlemmerIGruppe(enhetsnummer: String): GroupMembers {
        val gruppeNavn = ENHET_GROUP_PREFIX + enhetsnummer
        val groupId = hentGruppeIdGittNavn(gruppeNavn)

        // TODO: paginering når det er mer enn 500 saksbehandlere på enhet
        val url = baseUrl.resolve("groups/${groupId}/members?\$top=500&\$select=onPremisesSamAccountName")
        val respons = httpClientM2m.get<GroupMembers>(
            url, GetRequest(additionalHeaders = listOf(Header("ConsistencyLevel", "eventual")))
        ) ?: GroupMembers()
        if (respons.members.isEmpty()) {
            log.warn("MsGraph fant ingen medlemmer i gruppe $gruppeNavn")
        }
        return respons
    }

    private fun hentGruppeIdGittNavn(gruppeNavn: String): UUID {
        val url = baseUrl.resolve("groups?\$filter=${equalsFilter(displayName = gruppeNavn)}&\$select=id,mailNickname")
        val respons = httpClientM2m.get<MemberOf>(
            url, GetRequest(additionalHeaders = listOf(Header("ConsistencyLevel", "eventual")))
        )
        return requireNotNull(respons?.groups?.first()?.id) { "Kunne ikke hente gruppe-ID fra msGraph gitt gruppenavn $gruppeNavn"}
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

    private fun equalsFilter(displayName: String): String {
        return "displayName%20eq%20\'$displayName\'"
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

data class User(
    @param:JsonProperty("onPremisesSamAccountName")
    val navIdent: String,
)

data class GroupMembers(
    @param:JsonProperty("value")
    val members: List<User> = emptyList()
)

