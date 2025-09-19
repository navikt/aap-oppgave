package no.nav.aap.oppgave.klienter.nom.skjerming

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI

private data class EgenansattRequest(val personident: String)

interface SkjermingKlient {
    fun erSkjermet(ident: String): Boolean
}

class NomSkjermingKlient: SkjermingKlient {
    private val url = URI.create(requiredConfigForKey("integrasjon.nom.url"))

    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.nom.scope"),
    )

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus
    )

    override fun erSkjermet(ident: String): Boolean {
        val egenansattUrl = url.resolve("/skjermet")
        val request = PostRequest(
            body = EgenansattRequest(ident),
        )
        val eksistererSkjermet = client.post<EgenansattRequest, Boolean>(egenansattUrl, request) ?: throw SkjermingException("Kunne ikke hente skjermingstatus for ident")
        return eksistererSkjermet
    }

}
