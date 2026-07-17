package no.nav.aap.oppgave

import no.nav.aap.oppgave.enhet.EnhetDto
import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
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
    fun tilDto(): ReturInformasjon = ReturInformasjon(
        status = status,
        årsaker = årsaker,
        begrunnelse = begrunnelse,
        endretAv = endretAv
    )

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
) {
    fun tilDto(): ForrigeKvalitetssikrerInfo = ForrigeKvalitetssikrerInfo(
        forrigeKvalitetssikrerIdent = forrigeKvalitetssikrerIdent,
        forrigeKvalitetssikrerNavn = forrigeKvalitetssikrerNavn
    )
}

data class Oppgave(
    val id: Long? = null,
    val personIdent: String? = null,
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
    val returInformasjon: ReturInfo? = null,
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
    val tilbakekrevingsVars: TilbakekrevingsVars? = null,
    val forrigeKvalitetssikrerInfo: ForrigeKvalitetssikrer? = null,
) {
    /**
     * Oppfølgingsenhet skal alltid prioriteres dersom den er satt.
     * Brukes for å sikre at oppgaver havner i riktig kø i oppgavelisten.
     **/
    val enhetForKø: String = oppfølgingsenhet ?: enhet

    val erPåVent: Boolean = påVentTil != null

    val erÅpen: Boolean = status == Status.OPPRETTET

    init {
        if (journalpostId == null) {
            if (saksnummer == null) {
                throw IllegalArgumentException("Saksnummer kan ikke være null dersom journalpostId er null")
            }
        }
    }

    fun oppgaveId() = OppgaveId(
        requireNotNull(id) { "Oppgave har ingen id" },
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

    fun tilOppgaveDto(): OppgaveDto {
        return OppgaveDto(
            id = requireNotNull(id) { "Oppgave må ha ID" },
            saksnummer = saksnummer,
            journalpostId = journalpostId,
            avklaringsbehovKode = avklaringsbehovKode,
            behandlingOpprettet = behandlingOpprettet,
            behandlingstype = behandlingstype,
            status = status,
            endretAv = endretAv,
            endretTidspunkt = endretTidspunkt,
            versjon = versjon,
            harFortroligAdresse = harFortroligAdresse,
            harUlesteDokumenter = harUlesteDokumenter,
            vurderingsbehov = vurderingsbehov,
            personIdent = requireNotNull(personIdent) {
                "Personident kan ikke være null for OppgaveDto"
            },
            personNavn = personNavn,
            behandlingRef = behandlingRef,
            enhet = enhet,
            enhetForrigeOppgave = enhetForrigeOppgave,
            oppfølgingsenhet = oppfølgingsenhet,
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            påVentTil = påVentTil,
            påVentÅrsak = påVentÅrsak,
            utløptVentefrist = utløptVentefrist,
            venteBegrunnelse = venteBegrunnelse,
            forrigePåVentÅrsak = forrigePåVentÅrsak,
            forrigeVenteBegrunnelse = forrigeVenteBegrunnelse,
            returStatus = returStatus,
            returInformasjon = returInformasjon?.tilDto(),
            årsakerTilBehandling = årsakerTilBehandling,
            årsakTilOpprettelse = årsakTilOpprettelse,
            reservertAv = reservertAv,
            reservertAvNavn = reservertAvNavn,
            reservertTidspunkt = reservertTidspunkt,
            opprettetAv = opprettetAv,
            opprettetTidspunkt = opprettetTidspunkt,
            erSkjermet = erSkjermet,
            markeringer = markeringer,
            tilbakekrevingsVarsDto = tilbakekrevingsVars?.tilDto(),
            forrigeKvalitetssikrerInfo = forrigeKvalitetssikrerInfo?.tilDto(),
        )
    }
}