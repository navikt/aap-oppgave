package no.nav.aap.oppgave.filter

import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import java.time.LocalDateTime


enum class FilterType {
    GENERELL,
    ALLE_OPPGAVER,
    KVALITETSSIKRING,
}

data class Filter(
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
    val type: FilterType,
) {
    fun tilResponse(): FilterResponse {
        return FilterResponse(
            id = id,
            navn = navn,
            beskrivelse = beskrivelse,
            type = type.tilDto()
        )
    }

    fun tilDto(): FilterDto {
        return FilterDto(
            id = id,
            navn = navn,
            beskrivelse = beskrivelse,
            avklaringsbehovKoder = avklaringsbehovKoder,
            behandlingstyper = behandlingstyper,
            enheter = enheter,
            veileder = veileder,
            inkluderteMarkeringer = inkluderteMarkeringer,
            ekskluderteMarkeringer = ekskluderteMarkeringer,
            opprettetAv = opprettetAv,
            opprettetTidspunkt = opprettetTidspunkt,
            endretAv = endretAv,
            endretTidspunkt = endretTidspunkt,
            type = type.tilDto()
        )
    }
}

data class OpprettFilter(
    val navn: String,
    val beskrivelse: String,
    val avklaringsbehovKoder: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val enhetFilter: List<EnhetFilter>? = null,
    val markeringer: List<MarkeringFilter> = emptyList(),
    val type: FilterTypeDto = FilterTypeDto.GENERELL,
)

data class EnhetFilter(
    val enhetNr: String,
    val filtermodus: Filtermodus
)

data class MarkeringFilter(
    val markeringType: MarkeringForBehandling,
    val filtermodus: Filtermodus
)

data class OppdaterFilter(
    val id: Long,
    val navn: String,
    val beskrivelse: String,
    val avklaringsbehovtyper: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val markeringer: List<MarkeringFilter> = emptyList(),
    val enhetFilter: List<EnhetFilter>? = null,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
)

fun FilterType.tilDto(): FilterTypeDto {
    return when (this) {
        FilterType.GENERELL -> FilterTypeDto.GENERELL
        FilterType.ALLE_OPPGAVER -> FilterTypeDto.ALLE_OPPGAVER
        FilterType.KVALITETSSIKRING -> FilterTypeDto.KVALITETSSIKRING
    }
}
