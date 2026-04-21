package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingBehandlingsstatus
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.tilbakekreving.TilbakeKrevingAvklaringsbehovKoder
import no.nav.aap.oppgave.verdityper.Behandlingstype

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
        venteInformasjon = null,
        totaltFeilutbetaltBeløp = this.totaltFeilutbetaltBeløp,
        tilbakekrevingsUrl = this.saksbehandlingURL
    )
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
    }
