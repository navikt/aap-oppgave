package no.nav.aap.oppgave.forespørsel

import no.nav.aap.oppgave.dialogmedbehandler.ForespørselHendelseDto
import no.nav.aap.oppgave.verdityper.ForespørselHendelseTypeDto
import java.time.LocalDateTime
import java.util.UUID

enum class ForespørselHendelseType {
    FORESPØRSEL_OPPRETTET,
    FORESPØRSEL_AVSLUTTET,
}

data class OpprettForespørselHendelse(
    val behandlingRef: UUID,
    val type: ForespørselHendelseType,
)

data class ForespørselHendelse(
    val behandlingRef: UUID,
    val type: ForespørselHendelseType,
    val opprettetTidspunkt: LocalDateTime,
    val opprettetAv: String,
)

fun ForespørselHendelse.tilDto(): ForespørselHendelseDto {
    return ForespørselHendelseDto(
        behandlingRef = behandlingRef,
        type = type.tilDto(),
        opprettetTidspunkt = opprettetTidspunkt,
        opprettetAv = opprettetAv
    )
}

fun ForespørselHendelseType.tilDto(): ForespørselHendelseTypeDto {
    return when (this) {
        ForespørselHendelseType.FORESPØRSEL_OPPRETTET -> ForespørselHendelseTypeDto.FORESPØRSEL_OPPRETTET
        ForespørselHendelseType.FORESPØRSEL_AVSLUTTET -> ForespørselHendelseTypeDto.FORESPØRSEL_AVSLUTTET
    }
}