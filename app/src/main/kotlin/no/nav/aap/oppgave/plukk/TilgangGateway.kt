package no.nav.aap.oppgave.plukk

import java.net.URI
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.tilbakekreving.TilbakeKrevingAvklaringsbehovKoder
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.TilbakekrevingTilgangRequest
import no.nav.aap.tilgang.TilgangResponse

object TilgangGateway {
    private val baseUrl = URI.create(requiredConfigForKey("integrasjon.tilgang.url"))
    private val config = ClientConfig(scope = requiredConfigForKey("integrasjon.tilgang.scope"))

    private val client = RestClient.withDefaultResponseHandler(
        config = config,
        tokenProvider = OnBehalfOfTokenProvider,
        prometheus = prometheus
    )

    fun sjekkTilgang(
        avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
        token: OidcToken,
        operasjon: Operasjon = Operasjon.SAKSBEHANDLE
    ): Boolean {
        return if (avklaringsbehovReferanse.journalpostId != null) {
            harTilgangTilJournalpost(
                JournalpostTilgangRequest(
                    journalpostId = avklaringsbehovReferanse.journalpostId!!,
                    avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = operasjon
                ), token
            )
        } else if (avklaringsbehovReferanse.behandlingstype == Behandlingstype.TILBAKEKREVING) {
            harTilgangTilTilbakekrevingsbehandling(
                TilbakekrevingTilgangRequest(
                    behandlingsreferanse = avklaringsbehovReferanse.referanse!!,
                    saksnummer = avklaringsbehovReferanse.saksnummer!!,
                    påkrevdRoller = utledPåkrevdRolleForTilbakekreving(avklaringsbehovReferanse.avklaringsbehovKode),
                    operasjon = operasjon
                ), token
            )
        } else {
            harTilgangTilBehandling(
                BehandlingTilgangRequest(
                    behandlingsreferanse = avklaringsbehovReferanse.referanse!!,
                    avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = operasjon
                ), token
            )
        }
    }

    private fun harTilgangTilBehandling(body: BehandlingTilgangRequest, currentToken: OidcToken): Boolean {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/behandling"),
                request = httpRequest
            )
        )
        return respons.tilgang
    }

    private fun harTilgangTilJournalpost(body: JournalpostTilgangRequest, currentToken: OidcToken): Boolean {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/journalpost"),
                request = httpRequest
            )
        )
        return respons.tilgang
    }

    private fun harTilgangTilTilbakekrevingsbehandling(
        body: TilbakekrevingTilgangRequest,
        currentToken: OidcToken
    ): Boolean {
        val httpRequest = PostRequest(
            body = body,
            currentToken = currentToken
        )
        val respons = requireNotNull(
            client.post<_, TilgangResponse>(
                uri = baseUrl.resolve("/tilgang/tilbakekreving"),
                request = httpRequest
            )
        )
        return respons.tilgang
    }

    private fun utledPåkrevdRolleForTilbakekreving(avklaringsbehovKode: String): List<Rolle> {
        val kode = TilbakeKrevingAvklaringsbehovKoder.fraKode(avklaringsbehovKode)
        return when (kode) {
            TilbakeKrevingAvklaringsbehovKoder.BESLUTTER_VEDTAK_TILBAKEKREVING -> listOf(Rolle.BESLUTTER)
            TilbakeKrevingAvklaringsbehovKoder.SAKSBEHANDLE_TILBAKEKREVING -> listOf(Rolle.SAKSBEHANDLER_NASJONAL)
        }
    }
}