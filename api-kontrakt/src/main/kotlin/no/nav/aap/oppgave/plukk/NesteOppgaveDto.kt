package no.nav.aap.oppgave.plukk

import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.verdityper.OppgaveId

data class NesteOppgaveDto(
    val oppgaveId: OppgaveId,
    val avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
)
