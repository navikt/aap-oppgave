package no.nav.aap.oppgave

import no.nav.aap.oppgave.opprett.Avklaringsbehovtype
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import java.util.UUID

data class AvsluttOppgaveDto(
    val saksnummer: String? = null,
    val referanse: UUID? = null,
    val journalpostId: Long? = null,
    val avklaringsbehovtype: Avklaringsbehovtype,
)
