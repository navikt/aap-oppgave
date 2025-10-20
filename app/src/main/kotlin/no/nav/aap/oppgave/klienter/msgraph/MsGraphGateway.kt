package no.nav.aap.oppgave.klienter.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
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
import no.nav.aap.oppgave.metrikker.prometheus
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.util.*
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.tokenx.OnBehalfOfTokenProvider as TexasOnBehalfOfTokenProvider

interface IMsGraphGateway {
    fun hentEnhetsgrupper(ident: String, currentToken: OidcToken): MemberOf
    fun hentFortroligAdresseGruppe(ident: String, currentToken: OidcToken): MemberOf
    fun hentMedlemmerIGruppe(enhetsnummer: String): GroupMembers
}

class MsGraphGateway(
    prometheus: PrometheusMeterRegistry
) : IMsGraphGateway {
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

    private val log = LoggerFactory.getLogger(MsGraphGateway::class.java)

    override fun hentEnhetsgrupper(ident: String, currentToken: OidcToken): MemberOf =
        enhetsgrupperCache.get(ident) {
            val url =
                baseUrl.resolve("me/memberOf?\$count=true&\$top=999&\$filter=${starterMedFilter(ENHET_GROUP_PREFIX)}")
            val respons = httpClient.get<MemberOf>(
                url,
                GetRequest(
                    currentToken = currentToken,
                    additionalHeaders = listOf(Header("ConsistencyLevel", "eventual"))
                )
            ) ?: MemberOf()

            if (respons.groups.isEmpty()) {
                log.warn("MsGraph fant ingen enhetsgrupper for bruker $ident")
            }
            respons
        }


    override fun hentMedlemmerIGruppe(enhetsnummer: String): GroupMembers =
        medlemmerCache.get(enhetsnummer) {
            val gruppeNavn = ENHET_GROUP_PREFIX + enhetsnummer
            val groupId = hentGruppeIdGittNavn(gruppeNavn)

            // TODO: paginering når det er mer enn 500 saksbehandlere på enhet
            log.info("Henter gruppemedlemmer for gruppenavn $gruppeNavn")
            val url =
                baseUrl.resolve("groups/${groupId}/members?\$top=500&\$select=onPremisesSamAccountName,givenName,surname")

            val respons = httpClientM2m.get<GroupMembers>(
                url, GetRequest(additionalHeaders = listOf(Header("ConsistencyLevel", "eventual")))
            ) ?: GroupMembers()

            if (respons.members.isEmpty()) {
                log.warn("MsGraph fant ingen medlemmer i gruppe $gruppeNavn")
            } else if (respons.members.size == 500) {
                log.warn("Hentet 500 medlemmer i gruppe $gruppeNavn.")
            }

            respons
        }

    private fun hentGruppeIdGittNavn(gruppeNavn: String): UUID {
        val url = baseUrl.resolve("groups?\$filter=${equalsFilter(displayName = gruppeNavn)}&\$select=id,mailNickname")
        val respons = httpClientM2m.get<MemberOf>(
            url, GetRequest(additionalHeaders = listOf(Header("ConsistencyLevel", "eventual")))
        )
        return requireNotNull(respons?.groups?.first()?.id) { "Kunne ikke hente gruppe-ID fra msGraph gitt gruppenavn $gruppeNavn" }
    }

    override fun hentFortroligAdresseGruppe(ident: String, currentToken: OidcToken): MemberOf =
        fortroligAdresseCache.get(ident) {
            val url =
                baseUrl.resolve("me/memberOf?\$count=true&\$top=1&\$filter=${starterMedFilter(FORTROLIG_ADRESSE_GROUP)}")

            httpClient.get<MemberOf>(
                url,
                GetRequest(
                    currentToken = currentToken,
                    additionalHeaders = listOf(Header("ConsistencyLevel", "eventual"))
                )
            ) ?: MemberOf()
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

        private val enhetsgrupperCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build<String, MemberOf>()

        private val medlemmerCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build<String, GroupMembers>()

        private val fortroligAdresseCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build<String, MemberOf>()

        init {
            CaffeineCacheMetrics.monitor(prometheus, enhetsgrupperCache, "msgraph_enhetsgrupper")
            CaffeineCacheMetrics.monitor(prometheus, medlemmerCache, "msgraph_medlemmer")
            CaffeineCacheMetrics.monitor(prometheus, fortroligAdresseCache, "msgraph_fortrolig_adresse")
        }
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
    @param:JsonProperty("givenName")
    val fornavn: String,
    @param:JsonProperty("surname")
    val etternavn: String,
)

data class GroupMembers(
    @param:JsonProperty("value")
    val members: List<User> = emptyList()
)

