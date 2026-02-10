package no.nav.aap.oppgave.tilbakekreving

import java.math.BigDecimal

data class TilbakekrevingVars (
    val oppgaveId: Long,
    val beløp: BigDecimal,
    val url: String
    )