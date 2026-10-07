package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.PåminnelseStatus
import java.time.LocalDateTime
import java.util.UUID

data class OppdaterPåminnelseRequest(
    val referanse: UUID,
    val påminnelseDato: LocalDateTime?,
    val påminnelseStatus: PåminnelseStatus?,
)
