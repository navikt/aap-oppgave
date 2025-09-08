package no.nav.aap.oppgave.klienter.nom.skjerming

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import java.net.URI

interface SkjermingKlient {
    fun erEgenansattBulk(personidenter: List<String>): Boolean
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

    override fun erEgenansattBulk(personidenter: List<String>): Boolean {
        val egenansattUrl = url.resolve("/skjermetBulk")
        val request = PostRequest(
            body = SkjermetDataBulkRequestDTO(personidenter)
        )

        val response: Map<String, Boolean>  = client.post(egenansattUrl, request) ?: throw SkjermingException("Feil ved henting av skjerming")
        val eksistererSkjermet = response.values.any { identIsSkjermet -> identIsSkjermet }

        return eksistererSkjermet
    }

}

internal data class SkjermetDataBulkRequestDTO(val personidenter: List<String>)
