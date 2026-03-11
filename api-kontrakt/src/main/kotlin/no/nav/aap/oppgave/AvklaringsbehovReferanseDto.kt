package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.util.UUID

data class AvklaringsbehovReferanseDto(
    val referanse: UUID? = null,
    val journalpostId: Long? = null,
    val avklaringsbehovKode: String,
    val behandlingstype: Behandlingstype
)
