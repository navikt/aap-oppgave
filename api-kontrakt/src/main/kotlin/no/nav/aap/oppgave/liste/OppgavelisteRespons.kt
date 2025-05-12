package no.nav.aap.oppgave.liste

import no.nav.aap.oppgave.OppgaveDto

data class OppgavelisteRespons(
    val antallTotalt: Int,
    val oppgaver: List<OppgaveDto>,
    val antallGjenstaaende: Int? = null
)