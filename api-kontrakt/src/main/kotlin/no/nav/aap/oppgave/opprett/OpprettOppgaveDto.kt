package no.nav.aap.oppgave.opprett

import no.nav.aap.oppgave.verdityper.AvklaringsbehovType
import java.time.LocalDateTime
import java.util.UUID

data class OpprettOppgaveDto(
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovType: AvklaringsbehovType,
)