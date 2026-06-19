package no.nav.aap.oppgave.drift

import java.time.LocalDateTime
import java.util.UUID
import no.nav.aap.oppgave.filter.FilterType
import no.nav.aap.oppgave.filter.Filtermodus
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import no.nav.aap.oppgave.verdityper.Status


internal data class OppgaveDriftsinfoDTO(
    val oppgaveId: Long,
    val behandlingRef: UUID,
    val status: Status,
    val enhet: String,
    val oppfølgingsenhet: String?,
    val reservertAv: String?,
    val veilederArbeid: String?,
    val veilederSykdom: String?,
    val opprettetTidspunkt: LocalDateTime,
    val endretTidspunkt: LocalDateTime?,
    val avklaringsbehovKode: String,
    val historikk: List<OppgaveHistorikkDto>,
)

data class DriftFilterResponsDTO(
    val filtre: List<FilterDriftResponse>,
    val avklaringsbehovUtenFilter: List<AvklaringsbehovDto>,
)

data class FilterDriftResponse(
    val id: Long,
    val navn: String,
    val beskrivelse: String,
    val type: FilterType,
    val avklaringsbehov: Set<AvklaringsbehovDto>,
    val behandlingstyper: Set<Behandlingstype>,
    val inkluderteEnheter: List<String>,
    val ekskluderteEnheter: List<String>,
    val inkluderteMarkeringer: List<MarkeringForBehandling>,
    val ekskluderteMarkeringer: List<MarkeringForBehandling>,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String?,
    val endretTidspunkt: LocalDateTime?,
)

data class FilterDriftRequest(
    val id: Long? = null,
    val navn: String,
    val beskrivelse: String,
    val type: FilterType = FilterType.GENERELL,
    val avklaringsbehovKoder: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val enheter: List<EnhetDriftRequest> = emptyList(),
    val markeringer: List<MarkeringDriftRequest> = emptyList(),
)

data class EnhetDriftRequest(
    val enhet: String,
    val filtermodus: Filtermodus,
)

data class MarkeringDriftRequest(
    val type: MarkeringForBehandling,
    val filtermodus: Filtermodus,
)

data class AvklaringsbehovDto(val kode: String, val navn: String)

data class SlettFilterRequest(
    val id: Long,
)

internal data class OppgaveHistorikkDto(
    val status: Status,
    val reservertAv: String?,
    val reservertTidspunkt: LocalDateTime?,
    val endretAv: String?,
    val endretTidspunkt: LocalDateTime?,
    val enhet: String,
    val oppfølgingsenhet: String?
)
