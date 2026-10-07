package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.PåminnelseStatus
import java.time.LocalDateTime

data class ForespørselSendtTilBehandler(
    val påminnelseDato: LocalDateTime?,
    val påminnelseStatus: PåminnelseStatus?,
) {
    fun tilDto(): ForespørselSendtTilBehandlerDto = ForespørselSendtTilBehandlerDto(
        påminnelseDato = påminnelseDato,
        påminnelseStatus = påminnelseStatus,
    )
}
