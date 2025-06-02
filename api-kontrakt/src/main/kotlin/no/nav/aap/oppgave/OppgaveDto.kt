package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

enum class ReturStatus {
    RETUR_FRA_BESLUTTER,
    RETUR_FRA_KVALITETSSIKRER,
}

data class ReturInformasjon(
    val status: ReturStatus,
    val årsaker: List<ÅrsakTilReturKode>,
    val begrunnelse: String,
    val endretAv: String,
)

enum class ÅrsakTilReturKode {
    MANGELFULL_BEGRUNNELSE,
    MANGLENDE_UTREDNING,
    FEIL_LOVANVENDELSE,
    ANNET
}

/**
 * @param enhet Enhetsnummeret til enheten som er koblet til oppgaven.
 * TODO: Fjern sensitive felter
 */
data class OppgaveDto(
    val id: Long? = null,
    val personIdent: String? = null,
    val personNavn: String? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val enhet: String,
    val oppfølgingsenhet: String?,
    val veileder: String? = null, // TODO fjernes når kontrakt i frontend er oppdatert
    val veilederArbeid: String? = null,
    val veilederSykdom: String? = null,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: String,
    val status: Status = Status.OPPRETTET,
    val behandlingstype: Behandlingstype,
    val påVentTil: LocalDate? = null,
    val påVentÅrsak: String? = null,
    val venteBegrunnelse: String? = null,
    @Deprecated("Bruk returInformasjon")
    val returStatus: ReturStatus? = null,
    val returInformasjon: ReturInformasjon? = null,
    val årsakerTilBehandling: List<String> = emptyList(),
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
    val versjon: Long = 0,
    val harFortroligAdresse: Boolean? = false
) {
    init {
        if (journalpostId == null) {
            if (saksnummer == null || behandlingRef == null) {
                throw IllegalArgumentException("Saksnummer og behandlingRef kan ikke være null dersom journalpostId er null")
            }
        }
    }

    fun tilAvklaringsbehovReferanseDto(): AvklaringsbehovReferanseDto {
        return AvklaringsbehovReferanseDto(
            this.saksnummer,
            this.behandlingRef,
            this.journalpostId,
            this.avklaringsbehovKode
        )
    }

    fun enhetForKø(): String {
        return this.oppfølgingsenhet ?: this.enhet
    }

}
