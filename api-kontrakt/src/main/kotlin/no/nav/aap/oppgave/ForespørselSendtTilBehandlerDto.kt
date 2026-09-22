package no.nav.aap.oppgave

import java.time.LocalDateTime

data class ForespørselSendtTilBehandlerDto(
    val påminnelseDato: LocalDateTime?,
    val påminnelseStatus: String?,
)
