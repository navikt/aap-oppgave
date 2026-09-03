package no.nav.aap.oppgave.dialogmedbehandler

import no.nav.aap.oppgave.verdityper.ForespørselHendelseTypeDto
import java.time.LocalDateTime
import java.util.UUID

data class ForespørselHendelseDto(
    val behandlingRef: UUID,
    val type: ForespørselHendelseTypeDto,
    val opprettetTidspunkt: LocalDateTime,
    val opprettetAv: String,
)