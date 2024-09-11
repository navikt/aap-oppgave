package no.nav.aap.oppgave.filter

import no.nav.aap.oppgave.opprett.AvklaringsbehovKode

data class Filter(
    val id: Long,
    val navn: String,
    val avklaringsbehovKoder: Set<AvklaringsbehovKode> = emptySet()
)