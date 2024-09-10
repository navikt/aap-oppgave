package no.nav.aap.oppgave.avklaringsbehov

import java.time.LocalDateTime

enum class OppgaveStatus {
    OPPRETTET,
    AVSLUTTET
}

data class Oppgave(
    val id: OppgaveId? = null,
    val saksnummer: Saksnummer,
    val behandlingRef: BehandlingRef,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: AvklaringsbehovKode,
    val oppgaveStatus: OppgaveStatus = OppgaveStatus.OPPRETTET,
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
)
