package no.nav.aap.oppgave.filter

import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.time.LocalDateTime

data class FilterDto(
    val id: Long? = null,
    val beskrivelse: String,
    val avklaringsbehovKoder: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
)