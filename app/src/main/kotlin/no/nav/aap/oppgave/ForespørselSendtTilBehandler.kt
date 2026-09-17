package no.nav.aap.oppgave

import java.time.LocalDateTime

data class ForespørselSendtTilBehandler(
    val påminnelseDato: LocalDateTime?,
) {
    fun tilDto(): ForespørselSendtTilBehandlerDto = ForespørselSendtTilBehandlerDto(
        påminnelseDato = påminnelseDato,
    )
}
