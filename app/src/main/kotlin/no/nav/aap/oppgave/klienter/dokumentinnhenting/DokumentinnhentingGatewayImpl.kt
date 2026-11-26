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
import java.io.InputStream
import java.net.URI
import java.util.UUID

class DokumentinnhentingGatewayImpl
private constructor(
    private val httpClient: RestClient<InputStream>,
) : DokumentinnhentingGateway {
    private val baseUrl = URI.create(requiredConfigForKey("INTEGRASJON_DOKUMENTINNHENTING_URL"))

    override fun hentPåminnelserForBehandling(behandlingsreferanse: UUID): List<PåminnelseInfo> {
        val url = baseUrl.resolve("/dialogmelding/paaminnelse/$behandlingsreferanse/hent")
        val respons = requireNotNull(
            httpClient.get<PåminnelseInfoResponse>(url, GetRequest())
        ) {
            "Feil ved henting av påminnelseinfo for behandling $behandlingsreferanse"
        }

        return respons.påminnelser.map {
            PåminnelseInfo(
                status = mapPåminnelseStatus(it.status),
                påminnelseDato = it.påminnelseDato,
                opprettetTidspunkt = it.opprettetTidspunkt,
            )
        }
    }

    companion object {
        private fun getClientConfig() = ClientConfig(
            scope = requiredConfigForKey("INTEGRASJON_DOKUMENTINNHENTING_SCOPE")
        )

        fun withClientCredentialsRestClient() =
            DokumentinnhentingGatewayImpl(
                RestClient.withDefaultResponseHandler(
                    config = getClientConfig(),
                    tokenProvider = AzureM2MTokenProvider,
                    prometheus = prometheus
                )
            )

    }
}

private fun mapPåminnelseStatus(status: PåminnelseStatusResponse): PåminnelseStatus {
    return when (status) {
        PåminnelseStatusResponse.PLANLAGT -> PåminnelseStatus.PLANLAGT
        PåminnelseStatusResponse.SENDT -> PåminnelseStatus.SENDT
        PåminnelseStatusResponse.AVBRUTT -> PåminnelseStatus.AVBRUTT
    }
}