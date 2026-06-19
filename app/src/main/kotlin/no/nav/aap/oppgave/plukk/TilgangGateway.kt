package no.nav.aap.oppgave.plukk

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.tilbakekreving.TilbakeKrevingAvklaringsbehovKoder
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.TilbakekrevingTilgangRequest
import no.nav.aap.tilgang.TilgangGateway
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon

object TilgangService {

    suspend fun sjekkTilgang(
        avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
        token: OidcToken,
        operasjon: Operasjon = Operasjon.SAKSBEHANDLE
    ): Boolean {
        return if (avklaringsbehovReferanse.journalpostId != null) {
            TilgangGateway.harTilgangTilJournalpost(
                JournalpostTilgangRequest(
                    journalpostId = avklaringsbehovReferanse.journalpostId!!,
                    påkrevdRolle = avklaringsbehovReferanse.utledPåkrevdRolle(),
                    operasjon = operasjon
                ), token
            ).tilgang
        } else if (avklaringsbehovReferanse.behandlingstype == Behandlingstype.TILBAKEKREVING) {
            TilgangGateway.harTilgangTilTilbakekreving(
                TilbakekrevingTilgangRequest(
                    behandlingsreferanse = avklaringsbehovReferanse.referanse!!,
                    saksnummer = avklaringsbehovReferanse.saksnummer!!,
                    påkrevdRoller = utledPåkrevdRolleForTilbakekreving(avklaringsbehovReferanse.avklaringsbehovKode),
                    operasjon = operasjon
                ), token
            ).tilgang
        } else {
            TilgangGateway.harTilgangTilBehandling(
                BehandlingTilgangRequest(
                    behandlingsreferanse = avklaringsbehovReferanse.referanse!!,
                    påkrevdRolle = avklaringsbehovReferanse.utledPåkrevdRolle(),
                    operasjon = operasjon
                ), token
            ).tilgang
        }
    }

    private fun utledPåkrevdRolleForTilbakekreving(avklaringsbehovKode: String): List<Rolle> {
        val kode = TilbakeKrevingAvklaringsbehovKoder.fraKode(avklaringsbehovKode)
        return when (kode) {
            TilbakeKrevingAvklaringsbehovKoder.BESLUTTER_VEDTAK_TILBAKEKREVING -> listOf(Rolle.BESLUTTER)
            TilbakeKrevingAvklaringsbehovKoder.SAKSBEHANDLE_TILBAKEKREVING -> listOf(Rolle.SAKSBEHANDLER_NASJONAL)
        }
    }
}

private fun AvklaringsbehovReferanseDto.utledPåkrevdRolle(): List<Rolle> {
    return if (behandlingstype.fraBehandlingsflyt) {
        Definisjon.forKode(avklaringsbehovKode).løsesAv
    } else {
        PostmottakDefinisjon.forKode(avklaringsbehovKode).løsesAv
    }
}