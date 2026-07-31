package no.nav.aap.oppgave.filter

data class FilterResponse(
    val id: Long,
    val navn: String,
    val beskrivelse: String,
    val type: FilterTypeDto,
    val inneholderTilbakekreving: Boolean,
)

enum class FilterTypeDto {
    GENERELL,
    ALLE_OPPGAVER,
    KVALITETSSIKRING,
}
