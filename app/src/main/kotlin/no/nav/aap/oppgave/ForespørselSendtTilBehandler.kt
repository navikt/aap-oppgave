package no.nav.aap.oppgave

import no.nav.aap.oppgave.påminnelse.PåminnelseStatus
import java.time.LocalDate

data class ForespørselSendtTilBehandler(
    val påminnelseDato: LocalDate?,
    val påminnelseStatus: PåminnelseStatus?,
) {
    fun tilDto(): ForespørselSendtTilBehandlerDto = ForespørselSendtTilBehandlerDto(
        påminnelseDato = påminnelseDato,
        påminnelseAvbrutt = påminnelseStatus == PåminnelseStatus.AVBRUTT,
    )
}
