package no.nav.aap.oppgave.plukk

import no.nav.aap.oppgave.verdityper.Kilde
import no.nav.aap.oppgave.verdityper.OppgaveId

data class NesteOppgaveDto(
    val oppgaveId: OppgaveId,
    val kilde: Kilde,
    val referanse: String,
)
