package no.nav.aap.oppgave

import no.nav.aap.oppgave.opprett.Avklaringsbehovtype
import java.util.UUID

data class AvklaringsbehovReferanseDto(
    val saksnummer: String? = null,
    val referanse: UUID? = null,
    val journalpostId: Long? = null,
    val avklaringsbehovtype: Avklaringsbehovtype,
)
