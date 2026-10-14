package no.nav.aap.oppgave

import java.time.LocalDate

data class ForespørselSendtTilBehandler(
    val påminnelseDato: LocalDate?,
    val påminnelseAvbrutt: Boolean?,
) {
    fun tilDto(): ForespørselSendtTilBehandlerDto = ForespørselSendtTilBehandlerDto(
        påminnelseDato = påminnelseDato,
        påminnelseAvbrutt = påminnelseAvbrutt,
    )
}
