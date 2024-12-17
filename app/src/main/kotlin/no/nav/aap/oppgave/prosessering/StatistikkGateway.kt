package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import java.net.URI

object StatistikkGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.statistikk.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.statistikk.scope"))

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
    )

    fun avgiHendelse(oppgaveHendelse: OppgaveHendelse) {
        val httpRequest = PostRequest(
            body = oppgaveHendelse,
        )
        val respons = requireNotNull(
            client.post<_, String>(
                uri = baseUrl.resolve("/oppgave"),
                request = httpRequest
            )
        )
    }
}