package no.nav.aap.oppgave

import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.enhet.EnhetInfo
import no.nav.aap.oppgave.markering.Markering
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.ReturStatus
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.oppgave.verdityper.ÅrsakTilReturKode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ReturInfo(
    val status: ReturStatus,
    val årsaker: List<ÅrsakTilReturKode>,
    val begrunnelse: String,
    val endretAv: String,
) {
    fun tilReturInformasjonDto(): ReturInformasjonDto = ReturInformasjonDto(
        status = status,
        endretAv = endretAv,
        begrunnelse = begrunnelse,
        årsaker = årsaker
    )
}

@Suppress("PropertyName")
data class TilbakekrevingsVars(
    val tilbakekrevings_URL: String,
    val tilbakekrevings_beløp: BigDecimal
) {
    fun tilDto(): TilbakekrevingsVarsDto = TilbakekrevingsVarsDto(
        tilbakekrevings_URL = tilbakekrevings_URL,
        tilbakekrevings_beløp = tilbakekrevings_beløp
    )
}

data class ForrigeKvalitetssikrer(
    val forrigeKvalitetssikrerIdent: String,
    val forrigeKvalitetssikrerNavn: String? = null,
)

data class Oppgave(
    val id: Long,
    val personIdent: String,
    val personNavn: String? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID,
    val journalpostId: Long? = null,
    val enhet: String,
    val enhetForrigeOppgave: EnhetInfo? = null,
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
    val returInformasjon: ReturInfo? = null,
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
    val markeringer: List<Markering> = emptyList(),
    val tilbakekrevingsVars: TilbakekrevingsVars? = null,
    val forrigeKvalitetssikrerInfo: ForrigeKvalitetssikrer? = null,
    val uføreVedtak: UførevedtakRespons? = null,
) {
    /**
     * Oppfølgingsenhet skal alltid prioriteres dersom den er satt.
     * Brukes for å sikre at oppgaver havner i riktig kø i oppgavelisten.
     * 
     **/
    val enhetForKø: String = oppfølgingsenhet ?: enhet

    val erPåVent: Boolean = påVentTil != null

    val erÅpen: Boolean = status == Status.OPPRETTET

    val harStrengtFortroligAdresse: Boolean = enhet == Enhet.NAV_VIKAFOSSEN.kode

    init {
        if (journalpostId == null) {
            if (saksnummer == null) {
                throw IllegalArgumentException("Saksnummer kan ikke være null dersom journalpostId er null")
            }
        }
    }

    fun oppgaveId() = OppgaveId(
        id,
        versjon,
    )

    fun tilAvklaringsbehovReferanseDto(): AvklaringsbehovReferanseDto {
        return AvklaringsbehovReferanseDto(
            referanse = this.behandlingRef,
            saksnummer = this.saksnummer,
            journalpostId = this.journalpostId,
            avklaringsbehovKode = this.avklaringsbehovKode,
            behandlingstype = this.behandlingstype
        )
    }
}