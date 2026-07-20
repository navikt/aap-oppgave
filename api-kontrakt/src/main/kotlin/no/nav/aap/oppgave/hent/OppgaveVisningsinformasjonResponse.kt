package no.nav.aap.oppgave.hent

import no.nav.aap.oppgave.ReturInformasjonDto
import no.nav.aap.oppgave.markering.MarkeringDto
import java.time.LocalDate

data class OppgaveVisningsinformasjonResponse(
    val id: Long,
    val versjon: Long,
    val saksnummer: String?,
    val reservertAvNavn: String?,
    val reservertAvIdent: String?,
    val returInformasjon: ReturInformasjonDto?,
    val markeringer: List<MarkeringDto>,
    val påVentInfo: VenteInformasjonResponse?,
    val utløptVenteInfo: VenteInformasjonResponse?,
    val skjermingInfo: SkjermingInfoResponse,
    val harUlesteDokumenter: Boolean,
)

data class VenteInformasjonResponse(
    val påVentTil: LocalDate,
    val påVentÅrsak: String,
    val venteBegrunnelse: String?,
)

data class SkjermingInfoResponse(
    val harStrengtFortroligAdresse: Boolean,
    val harFortroligAdresse: Boolean,
    val erSkjermet: Boolean
)