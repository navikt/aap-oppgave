package no.nav.aap.oppgave.liste

import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.time.LocalDate

data class UtvidetOppgavelisteFilter (
    val årsaker: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val avklaringsbehovKoder: Set<String> = emptySet(), // Oppgavetype
    val statuser: Set<String> = emptySet()
)