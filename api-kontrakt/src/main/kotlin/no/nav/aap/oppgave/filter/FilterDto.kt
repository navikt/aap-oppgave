package no.nav.aap.oppgave.filter

import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode

data class FilterDto(
    val id: Long,
    val navn: String,
    val avklaringsbehovKoder: Set<AvklaringsbehovKode> = emptySet()
)