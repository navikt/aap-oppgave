package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingBehandlingsstatus
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.tilbakekreving.TilbakeKrevingAvklaringsbehovKoder
import no.nav.aap.oppgave.verdityper.Behandlingstype
import kotlin.collections.emptyList

fun TilbakekrevingsbehandlingOppdatertHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        personIdent = this.personIdent,
        saksnummer = this.saksnummer.toString(),
        referanse = this.behandlingref.referanse,
        journalpostId = null,
        behandlingStatus = this.behandlingStatus.tilBehandlingsstatus(),
        behandlingstype = Behandlingstype.TILBAKEKREVING,
        opprettetTidspunkt = this.sakOpprettet,
        avklaringsbehov = this.behandlingStatus.tilAvklaringsBehov(),
        vurderingsbehov = emptyList(),
        mottattDokumenter = emptyList(),
        årsakTilOpprettelse = null,
        venteInformasjon = if (this.behandlingStatus.erSaksbehandlerSteg()) {
            this.gjenopptas?.let { frist ->
                VenteInformasjon(
                    frist = frist,
                    årsakTilSattPåVent = this.venteGrunn?.name,
                    sattPåVentAv = TILBAKEKREVING,
                    begrunnelse = null,
                )
            }
        } else null,
        totaltFeilutbetaltBeløp = this.totaltFeilutbetaltBeløp,
        tilbakekrevingsUrl = this.saksbehandlingURL
    )
}

/* Ventestatus fra tilbake-løsningen gjelder kun saksbehandler-steget. Påvent skal ikke
 * henge igjen på en nyopprettet beslutter-oppgave når behandlingen sendes til godkjenning.
 *
 * Statusene som resulterer i avklaringsbehov SAKSBEHANDLE_TILBAKEKREVING, se tilAvklaringsBehov(). */
fun TilbakekrevingBehandlingsstatus.erSaksbehandlerSteg(): Boolean =
    when (this) {
        TilbakekrevingBehandlingsstatus.OPPRETTET,
        TilbakekrevingBehandlingsstatus.TIL_BEHANDLING,
        TilbakekrevingBehandlingsstatus.TIL_FORHÅNDSVARSEL,
        TilbakekrevingBehandlingsstatus.RETUR_FRA_BESLUTTER -> true

        TilbakekrevingBehandlingsstatus.TIL_BESLUTTER,
        TilbakekrevingBehandlingsstatus.TIL_GODKJENNING,
        TilbakekrevingBehandlingsstatus.AVSLUTTET -> false
    }

fun TilbakekrevingBehandlingsstatus.tilAvklaringsBehov(): List<AvklaringsbehovHendelse> {
    return when (this) {
        TilbakekrevingBehandlingsstatus.AVSLUTTET -> emptyList()
        TilbakekrevingBehandlingsstatus.OPPRETTET -> listOf(
            AvklaringsbehovHendelse(
                AvklaringsbehovKode(
                    TilbakeKrevingAvklaringsbehovKoder.SAKSBEHANDLE_TILBAKEKREVING.kode
                ), AvklaringsbehovStatus.OPPRETTET, emptyList()
            )
        )

        TilbakekrevingBehandlingsstatus.RETUR_FRA_BESLUTTER -> listOf(
            AvklaringsbehovHendelse(
                AvklaringsbehovKode(
                    TilbakeKrevingAvklaringsbehovKoder.SAKSBEHANDLE_TILBAKEKREVING.kode
                ), AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER, emptyList()
            )
        )

        TilbakekrevingBehandlingsstatus.TIL_BEHANDLING -> listOf(
            AvklaringsbehovHendelse(
                AvklaringsbehovKode(
                    TilbakeKrevingAvklaringsbehovKoder.SAKSBEHANDLE_TILBAKEKREVING.kode
                ), AvklaringsbehovStatus.OPPRETTET, emptyList()
            )
        )

        TilbakekrevingBehandlingsstatus.TIL_BESLUTTER -> listOf(
            AvklaringsbehovHendelse(
                AvklaringsbehovKode(
                    TilbakeKrevingAvklaringsbehovKoder.BESLUTTER_VEDTAK_TILBAKEKREVING.kode
                ), AvklaringsbehovStatus.OPPRETTET, emptyList()
            )
        )

        TilbakekrevingBehandlingsstatus.TIL_GODKJENNING -> listOf(
            AvklaringsbehovHendelse(
                AvklaringsbehovKode(
                    TilbakeKrevingAvklaringsbehovKoder.BESLUTTER_VEDTAK_TILBAKEKREVING.kode
                ), AvklaringsbehovStatus.OPPRETTET, emptyList()
            )
        )

        TilbakekrevingBehandlingsstatus.TIL_FORHÅNDSVARSEL -> listOf(
            AvklaringsbehovHendelse(
                AvklaringsbehovKode(
                    TilbakeKrevingAvklaringsbehovKoder.SAKSBEHANDLE_TILBAKEKREVING.kode
                ), AvklaringsbehovStatus.OPPRETTET, emptyList()
            )
        )
    }
}

fun TilbakekrevingBehandlingsstatus.tilBehandlingsstatus() =
    when (this) {
        TilbakekrevingBehandlingsstatus.OPPRETTET -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.TIL_BEHANDLING -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.AVSLUTTET -> BehandlingStatus.LUKKET
        TilbakekrevingBehandlingsstatus.TIL_BESLUTTER -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.RETUR_FRA_BESLUTTER -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.TIL_GODKJENNING -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.TIL_FORHÅNDSVARSEL -> BehandlingStatus.ÅPEN
    }
