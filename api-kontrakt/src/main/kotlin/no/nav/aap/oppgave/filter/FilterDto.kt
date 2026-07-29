package no.nav.aap.oppgave.filter

import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import java.time.LocalDateTime

data class FilterDto(
    val id: Long,
    val navn: String,
    val beskrivelse: String,
    val avklaringsbehovKoder: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val enheter: Set<String> = emptySet(),
    val veileder: String? = null,
    val inkluderteMarkeringer: Set<MarkeringForBehandling> = emptySet(),
    val ekskluderteMarkeringer: Set<MarkeringForBehandling> = emptySet(),
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
    val type: FilterTypeDto,
)

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
