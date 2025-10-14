package no.nav.aap.oppgave

import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

enum class ReturStatus {
    RETUR_FRA_BESLUTTER,
    RETUR_FRA_KVALITETSSIKRER,
    RETUR_FRA_VEILEDER,
    RETUR_FRA_SAKSBEHANDLER,
}

data class ReturInformasjon(
    val status: ReturStatus,
    val årsaker: List<ÅrsakTilReturKode>,
    val begrunnelse: String? = null,
    val endretAv: String,
)

enum class ÅrsakTilReturKode {
    MANGELFULL_BEGRUNNELSE,
    MANGLENDE_UTREDNING,
    FEIL_LOVANVENDELSE,
    ANNET,
    SKRIVEFEIL,
    FOR_DETALJERT,
    IKKE_INDIVIDUELL_OG_KONKRET,
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
    @Deprecated("Bytt til vurderingsbehov når frontend er oppdatert")
    val årsakerTilBehandling: List<String> = emptyList(),
    val vurderingsbehov: List<String> = emptyList(),
    val årsakTilOpprettelse: String? = null,
    val reservertAv: String? = null,
    val reservertAvNavn: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
    val versjon: Long = 0,
    val harFortroligAdresse: Boolean? = false,
    val harUlesteDokumenter: Boolean? = false,
    val markeringer: List<MarkeringDto> = emptyList(),
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
