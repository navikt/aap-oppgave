package no.nav.aap.oppgave.tilbakekreving

import no.nav.aap.komponenter.verdityper.Beløp

data class TilbakekrevingVars (
    val oppgaveId: Long,
    val beløp: Beløp,
    val url: String
    )