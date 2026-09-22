package no.nav.aap.oppgave

import java.time.LocalDateTime

data class ForespørselSendtTilBehandler(
    val påminnelseDato: LocalDateTime?,
    val påminnelseStatus: String?,
) {
    fun tilDto(): ForespørselSendtTilBehandlerDto = ForespørselSendtTilBehandlerDto(
        påminnelseDato = påminnelseDato,
        påminnelseStatus = påminnelseStatus,
    )
}
