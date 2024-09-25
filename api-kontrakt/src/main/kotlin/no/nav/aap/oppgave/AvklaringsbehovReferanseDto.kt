package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import java.util.UUID

data class AvklaringsbehovReferanseDto(
    val saksnummer: String? = null,
    val referanse: UUID? = null,
    val journalpostId: Long? = null,
    val avklaringsbehovKode: AvklaringsbehovKode,
)
