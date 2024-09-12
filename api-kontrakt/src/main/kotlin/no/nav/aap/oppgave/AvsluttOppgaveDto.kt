package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.AvklaringsbehovType
import java.util.UUID

data class AvsluttOppgaveDto(
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val avklaringsbehovType: AvklaringsbehovType,
)
