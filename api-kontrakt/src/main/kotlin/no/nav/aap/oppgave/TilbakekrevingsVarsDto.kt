package no.nav.aap.oppgave

import java.math.BigDecimal

@Suppress("PropertyName")
data class TilbakekrevingsVarsDto(
    val tilbakekrevings_URL: String,
    val tilbakekrevings_beløp: BigDecimal
)