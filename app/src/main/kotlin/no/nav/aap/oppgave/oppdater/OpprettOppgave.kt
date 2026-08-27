package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.UførevedtakDto
import no.nav.aap.oppgave.ReturInfo
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class OpprettOppgave(
    val personIdent: String,
    val personNavn: String? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID,
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
    val uføreVedtak: UførevedtakDto? = null,
    val venteBegrunnelse: String? = null,
    val returInformasjon: ReturInfo? = null,
    val vurderingsbehov: List<String> = emptyList(),
    val årsakTilOpprettelse: String? = null,
    val reservertAv: String? = null,
    val reservertAvNavn: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val harFortroligAdresse: Boolean,
    val erSkjermet: Boolean = false,
    val harUlesteDokumenter: Boolean = false,
) {
    init {
        if (journalpostId == null) {
            if (saksnummer == null) {
                throw IllegalArgumentException("Saksnummer kan ikke være null dersom journalpostId er null")
            }
        }
    }
}