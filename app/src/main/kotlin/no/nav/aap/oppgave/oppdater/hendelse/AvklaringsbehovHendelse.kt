package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.oppgave.AvklaringsbehovKode
import java.time.LocalDate
import java.time.LocalDateTime

data class AvklaringsbehovHendelse(
    val avklaringsbehovKode: AvklaringsbehovKode,
    val status: AvklaringsbehovStatus,
    val endringer: List<Endring>,
)

data class Endring(
    val status: AvklaringsbehovStatus,
    val tidsstempel: LocalDateTime = LocalDateTime.now(),
    val endretAv: String,
    val påVentTil: LocalDate?,
    val påVentÅrsak: String?,
    val begrunnelse: String? = null,
    val årsakTilRetur: List<ÅrsakTilReturKode> = emptyList()
)

fun List<AvklaringsbehovHendelse>.kelvinTokBehandlingAvVent(): Boolean {
    val sisteLukkedeVentebehov =
        this.filter { !it.status.erÅpent() }.maxByOrNull { ventebehov -> ventebehov.endringer.maxOf { it.tidsstempel } }
    if (sisteLukkedeVentebehov == null) {
        return false
    }

    // Endringen som lukket ventebehovet er gjort av Kelvin
    val sisteVentebehovLukketAvKelvin =
        sisteLukkedeVentebehov.endringer.maxByOrNull { it.tidsstempel }?.endretAv.equals(KELVIN, ignoreCase = true)

    // På siste endring der frist var satt, var frist i dag.
    val ventebehovHaddeFristIDag =
        sisteLukkedeVentebehov.endringer
            .filter { it.påVentTil?.isEqual(LocalDate.now()) == true }
            .maxByOrNull { it.tidsstempel } == sisteLukkedeVentebehov.endringer.filter { it.påVentTil != null }
            .maxByOrNull { it.tidsstempel }

    return sisteVentebehovLukketAvKelvin && ventebehovHaddeFristIDag
}