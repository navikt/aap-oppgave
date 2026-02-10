package no.nav.aap.oppgave.liste

import no.nav.aap.oppgave.ReturStatus
import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.math.BigDecimal
import java.time.LocalDate

data class UtvidetOppgavelisteFilter (
    val årsaker: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val avklaringsbehovKoder: Set<String> = emptySet(), // Oppgavetype
    val påVent: Boolean? = null,
    val returStatuser: Set<ReturStatus> = emptySet(),
    val markertHaster: Boolean? = null,
    val ventefristUtløpt : Boolean? = null,
    val beløpMerEnn : BigDecimal? = null,
    val beløpMindreEnn: BigDecimal? = null,
)