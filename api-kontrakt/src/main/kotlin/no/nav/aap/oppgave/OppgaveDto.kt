package no.nav.aap.oppgave

import no.nav.aap.oppgave.enhet.EnhetDto
import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class ReturStatus {
    RETUR_FRA_BESLUTTER,
    RETUR_FRA_KVALITETSSIKRER,
    RETUR_FRA_VEILEDER,
    RETUR_FRA_SAKSBEHANDLER,
}

@Deprecated("bruk ReturInformasjonDto")
data class ReturInformasjon(
    val status: ReturStatus,
    val årsaker: List<ÅrsakTilReturKode>,
    val begrunnelse: String,
    val endretAv: String,
)

data class ReturInformasjonDto(
    val status: ReturStatus,
    val årsaker: List<ÅrsakTilReturKode>,
    val begrunnelse: String,
    val endretAv: String,
)

@Suppress("PropertyName")
data class TilbakekrevingsVarsDto(
    val tilbakekrevings_URL : String,
    val tilbakekrevings_beløp: BigDecimal
)

data class ForrigeKvalitetssikrerInfo(
    val forrigeKvalitetssikrerIdent: String,
    val forrigeKvalitetssikrerNavn: String? = null,
)

enum class ÅrsakTilReturKode {
    MANGELFULL_BEGRUNNELSE,
    MANGLENDE_UTREDNING,
    FEIL_LOVANVENDELSE,
    ANNET,
    SKRIVEFEIL,
    FOR_DETALJERT,
    IKKE_INDIVIDUELL_OG_KONKRET,
    MANGLENDE_JOURNALFØRING,
    MANGLENDE_KILDEHENVISNING,
}

data class OppgaveDto(
    val id: Long,
    val personIdent: String,
    val personNavn: String? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID,
    val journalpostId: Long? = null,
    val enhet: String,
    val enhetForrigeOppgave: EnhetDto? = null,
    val oppfølgingsenhet: String?,
    val veilederArbeid: String? = null,
    val veilederSykdom: String? = null,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: String,
    val status: Status = Status.OPPRETTET,
    val behandlingstype: Behandlingstype,
    val påVentTil: LocalDate? = null,
    val påVentÅrsak: String? = null,
    val utløptVentefrist: LocalDate? = null,
    val venteBegrunnelse: String? = null,
    val forrigePåVentÅrsak: String? = null,
    val forrigeVenteBegrunnelse: String? = null,
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
    val erSkjermet: Boolean? = false,
    val harUlesteDokumenter: Boolean? = false,
    val markeringer: List<MarkeringDto> = emptyList(),
    val tilbakekrevingsVarsDto: TilbakekrevingsVarsDto? = null,
    val forrigeKvalitetssikrerInfo: ForrigeKvalitetssikrerInfo? = null,
) {
    fun oppgaveId() = OppgaveId(
        id,
        versjon,
    )
}
