package no.nav.aap.oppgave.klienter.dokumentinnhenting

import no.nav.aap.dokumentinnhenting.kontrakt.PåminnelseInfoResponse
import no.nav.aap.dokumentinnhenting.kontrakt.PåminnelseStatusResponse
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.påminnelse.PåminnelseInfo
import no.nav.aap.oppgave.påminnelse.PåminnelseStatus
import java.net.URI
import java.util.UUID

class DokumentinnhentingGatewayImpl : DokumentinnhentingGateway {
    private val baseUrl = URI.create(requiredConfigForKey("INTEGRASJON_DOKUMENTINNHENTING_URL"))
    private val clientConfig =
        ClientConfig(
            scope = requiredConfigForKey("INTEGRASJON_DOKUMENTINNHENTING_SCOPE")
        )
    private val httpClient =
        RestClient.withDefaultResponseHandler(
            config = clientConfig,
            tokenProvider = AzureM2MTokenProvider,
            prometheus = prometheus
        )

    override fun hentPåminnelserForBehandling(behandlingsreferanse: UUID): List<PåminnelseInfo> {
        val url = baseUrl.resolve("/dialogmelding/paaminnelse/$behandlingsreferanse/hent")
        val respons =
            httpClient.get<PåminnelseInfoResponse>(url, GetRequest())
                ?: throw DokumentinnhentingException("Feil ved henting av påminnelseinfo for behandling $behandlingsreferanse")

        return respons.påminnelser.map {
            PåminnelseInfo(
                status = mapPåminnelseStatus(it.status),
                påminnelseDato = it.påminnelseDato,
                opprettetTidspunkt = it.opprettetTidspunkt,
            )
        }
    }
}

private fun mapPåminnelseStatus(status: PåminnelseStatusResponse): PåminnelseStatus {
    return when (status) {
        PåminnelseStatusResponse.PLANLAGT -> PåminnelseStatus.PLANLAGT
        PåminnelseStatusResponse.SENDT -> PåminnelseStatus.SENDT
        PåminnelseStatusResponse.AVBRUTT -> PåminnelseStatus.AVBRUTT
    }
}