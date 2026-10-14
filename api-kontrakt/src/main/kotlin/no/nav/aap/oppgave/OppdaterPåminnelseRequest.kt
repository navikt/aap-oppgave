package no.nav.aap.oppgave

import java.time.LocalDate
import java.util.UUID

data class OppdaterPåminnelseRequest(
    val referanse: UUID,
    val påminnelseDato: LocalDate?,
    val påminnelseAvbrutt: Boolean?,
)
