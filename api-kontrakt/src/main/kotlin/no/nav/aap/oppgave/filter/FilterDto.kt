package no.nav.aap.oppgave.filter

import no.nav.aap.oppgave.verdityper.Behandlingstype

data class FilterDto(
    val id: Long,
    val navn: String,
    val avklaringsbehovKoder: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet()
)