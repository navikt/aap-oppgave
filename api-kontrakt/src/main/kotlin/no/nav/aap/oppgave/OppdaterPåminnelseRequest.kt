package no.nav.aap.oppgave

import java.time.LocalDateTime
import java.util.UUID

data class OppdaterPåminnelseRequest(
    val referanse: UUID,
    val påminnelseDato: LocalDateTime?,
    val påminnelseAvbrutt: Boolean?,
)
