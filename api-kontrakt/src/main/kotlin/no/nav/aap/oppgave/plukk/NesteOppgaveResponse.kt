package no.nav.aap.oppgave.plukk

import no.nav.aap.oppgave.Kilde
import no.nav.aap.oppgave.OppgaveId

data class NesteOppgaveResponse(
    val oppgaveId: OppgaveId,
    val kilde: Kilde,
    val referanse: String,
)
