package no.nav.aap.oppgave.plukk

import no.nav.aap.oppgave.AvklaringsbehovReferanseDto

data class NesteOppgaveDto(
    val oppgaveId: Long,
    val oppgaveVersjon: Long,
    val avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
)
