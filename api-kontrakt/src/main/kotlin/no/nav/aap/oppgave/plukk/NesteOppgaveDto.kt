package no.nav.aap.oppgave.plukk

import no.nav.aap.oppgave.verdityper.OppgaveId
import java.util.UUID

data class NesteOppgaveDto(
    val oppgaveId: OppgaveId,
    val saksnummer: String?,
    val behandlingRef: UUID?,
    val journalpostId: Long?,
    val avklaringsbehovKode: String
)
