package no.nav.aap.oppgave.klienter.nom

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import org.slf4j.LoggerFactory
import java.net.URI

private data class EgenansattRequest(val personident: String)

class NomKlient {

    private val log = LoggerFactory.getLogger(NomKlient::class.java)

    private val url = URI.create(requiredConfigForKey("integrasjon.nom.url"))

    private val config = ClientConfig(
        scope = requiredConfigForKey("integrasjon.nom.scope"),
    )

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        prometheus = prometheus
    )

    fun erEgenansatt(personident: String): Boolean {
        log.info("Sjekker om $personident er egenansatt")
        val egenansattUrl = url.resolve("skjermet")
        val request = PostRequest(
            body = EgenansattRequest(personident)
        )

        return client.post(egenansattUrl, request) ?: error("Uventet respons fra NOM")
    }

}
