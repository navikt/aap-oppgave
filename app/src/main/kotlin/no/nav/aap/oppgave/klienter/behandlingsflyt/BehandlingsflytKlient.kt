package no.nav.aap.oppgave.klienter.behandlingsflyt

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI
import java.util.UUID

object BehandlingsflytKlient {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.behandlingsflyt.url"))
    private val clientConfig =
        ClientConfig(
            scope = requiredConfigForKey("integrasjon.behandlingsflyt.scope")
        )
    private val httpClient =
        RestClient.withDefaultResponseHandler(
            config = clientConfig,
            tokenProvider = ClientCredentialsTokenProvider,
            prometheus = prometheus
        )

    fun hentRelevanteIdenterPåBehandling(behandlingsreferanse: UUID): List<String> {
        val url = baseUrl.resolve("/pip/api/behandling/$behandlingsreferanse/identer")
        val respons =
            httpClient.get<IdenterRespons>(url, GetRequest())
                ?: throw BehandlingsflytException("Feil ved henting av identer for behandling")

        return respons.barn + respons.søker
    }
}