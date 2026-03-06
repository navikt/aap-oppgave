package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.oppgave.AvklaringsbehovKode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.NoSuchElementException

data class AvklaringsbehovHendelse(
    val avklaringsbehovKode: AvklaringsbehovKode,
    val status: AvklaringsbehovStatus,
    val endringer: List<Endring>,
) {

    fun sisteEndring(status: AvklaringsbehovStatus = this.status): Endring {
        return try {
            endringer
                .sortedBy { it.tidsstempel }
                .last { it.status == status }
        } catch (e: NoSuchElementException) {
            throw IllegalStateException(
                "Ingen endringer med status $status. Endringer: ${this.endringer}. Avklaringsbehovkode: ${this.avklaringsbehovKode}",
                e
            )
        }
    }

    fun sistEndretAv(status: AvklaringsbehovStatus = this.status): String {
        return sisteEndring(status).endretAv
    }
}

data class Endring(
    val status: AvklaringsbehovStatus,
    val tidsstempel: LocalDateTime = LocalDateTime.now(),
    val endretAv: String,
    val påVentTil: LocalDate?,
    val påVentÅrsak: String?,
    val begrunnelse: String? = null,
    val årsakTilRetur: List<ÅrsakTilReturKode> = emptyList()
)