package no.nav.aap.oppgave.prosessering

import java.net.URI
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.statistikk.OppgaveHendelse

object StatistikkGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.statistikk.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.statistikk.scope"))

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