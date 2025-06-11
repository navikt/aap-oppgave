package no.nav.aap.oppgave.filter

import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.time.LocalDate
import java.time.LocalDateTime

interface Filter {
    val avklaringsbehovKoder: Set<String>
    val behandlingstyper: Set<Behandlingstype>
    val enheter: Set<String>
    val veileder: String?
}

data class FilterDto(
    val id: Long? = null,
    val navn: String,
    val beskrivelse: String,
    override val avklaringsbehovKoder: Set<String> = emptySet(),
    override val behandlingstyper: Set<Behandlingstype> = emptySet(),
    override val enheter: Set<String> = emptySet(),
    override val veileder: String? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
    val årsak: Set<String> = emptySet(),
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val status: Set<String> = emptySet(),
    ): Filter

data class TransientFilterDto(
    override val avklaringsbehovKoder: Set<String> = emptySet(),
    override val behandlingstyper: Set<Behandlingstype> = emptySet(),
    override val enheter: Set<String> = emptySet(),
    override val veileder: String? = null,
): Filter

