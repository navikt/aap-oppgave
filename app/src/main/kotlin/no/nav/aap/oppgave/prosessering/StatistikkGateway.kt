package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import java.net.URI

object StatistikkGateway {
    private val baseUrl = URI.create(requiredConfigForKey("INTEGRASJON_STATISTIKK_URL"))
    private val config = ClientConfig(scope = requiredConfigForKey("INTEGRASJON_STATISTIKK_SCOPE"))

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = AzureM2MTokenProvider,
        prometheus = prometheus
    )

    fun avgiHendelse(oppgaveHendelse: OppgaveHendelse) {
        val httpRequest = PostRequest(
            body = oppgaveHendelse,
        )
        requireNotNull(
            client.post<_, Unit>(
                uri = baseUrl.resolve("/oppgave"),
                request = httpRequest
            )
        )
    }
}