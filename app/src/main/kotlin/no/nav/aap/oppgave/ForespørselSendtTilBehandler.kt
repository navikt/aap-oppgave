package no.nav.aap.oppgave

import java.time.LocalDateTime

data class ForespørselSendtTilBehandler(
    val påminnelseDato: LocalDateTime?,
    val påminnelseAvbrutt: Boolean?,
) {
    fun tilDto(): ForespørselSendtTilBehandlerDto = ForespørselSendtTilBehandlerDto(
        påminnelseDato = påminnelseDato,
        påminnelseAvbrutt = påminnelseAvbrutt,
    )
}
