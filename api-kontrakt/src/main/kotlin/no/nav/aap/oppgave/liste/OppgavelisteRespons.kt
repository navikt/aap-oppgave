package no.nav.aap.oppgave.liste

import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.verdityper.Behandlingstype

data class OppgavelisteRespons(
    val antallTotalt: Int,
    val oppgaver: List<OppgaveDto>,
    val antallGjenstaaende: Int? = null,
    val sattFilterBehandlingstyper: Set<Behandlingstype>? = emptySet(),
)