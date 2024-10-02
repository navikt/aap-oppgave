package no.nav.aap.oppgave

import java.util.UUID

data class AvklaringsbehovReferanseDto(
    val saksnummer: String? = null,
    val referanse: UUID? = null,
    val journalpostId: Long? = null,
    val avklaringsbehovKode: String
)
