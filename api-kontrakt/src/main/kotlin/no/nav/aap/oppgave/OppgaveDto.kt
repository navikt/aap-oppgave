package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.AvklaringsbehovType
import no.nav.aap.oppgave.verdityper.OppgaveId
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDateTime
import java.util.UUID

data class OppgaveDto(
    val id: OppgaveId? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovType: AvklaringsbehovType,
    val status: Status = Status.OPPRETTET,
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
)
