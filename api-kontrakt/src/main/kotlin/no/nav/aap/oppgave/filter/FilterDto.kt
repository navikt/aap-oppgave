package no.nav.aap.oppgave.filter

data class FilterDto(
    val id: Long,
    val navn: String,
    val avklaringsbehovKoder: Set<String> = emptySet()
)