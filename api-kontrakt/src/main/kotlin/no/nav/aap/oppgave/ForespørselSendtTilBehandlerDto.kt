package no.nav.aap.oppgave

import java.time.LocalDate

data class ForespørselSendtTilBehandlerDto(
    val påminnelseDato: LocalDate?,
    val påminnelseAvbrutt: Boolean?,
)
