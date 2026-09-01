package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.oppgave.mottattdokument.MottattDokument
import no.nav.aap.oppgave.verdityper.BehandlingMetadata
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.uføreVedtak.UføreVedtak
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


const val KELVIN = "Kelvin"
const val TILBAKEKREVING = "Tilbakekreving"

/**
 * @param reserverTilPerAvklaringsbehov Oppgaver på avklaringsbehov (key) skal reserveres til saksbehandler (value)
 * @param relevanteIdenter Identer på barn lagret på behandlingen, som påvirker enhetsutledning
 */
data class OppgaveOppdatering(
    val personIdent: String,
    val saksnummer: String? = null,
    val referanse: UUID,
    val journalpostId: Long? = null,
    val behandlingStatus: BehandlingStatus,
    val behandlingstype: Behandlingstype,
    val opprettetTidspunkt: LocalDateTime,
    val avklaringsbehov: List<AvklaringsbehovHendelse>,
    val venteInformasjon: VenteInformasjon? = null,
    val vurderingsbehov: List<String>,
    val årsakTilOpprettelse: String?,
    val mottattDokumenter: List<MottattDokument>,
    val uføreVedtak: UføreVedtak? = null,
    val tattAvVentAutomatisk: Boolean = false,
    val reserverTilPerAvklaringsbehov: Map<String, String> = emptyMap(),
    val relevanteIdenter: List<String> = emptyList(),
    val totaltFeilutbetaltBeløp: BigDecimal? = null,
    val tilbakekrevingsUrl: String? = null,
    val behandlingMetadata: BehandlingMetadata? = null,
) {
    init {
        require(reserverTilPerAvklaringsbehov.values.none { it == KELVIN }) { "kan ikke reservere oppgave til KELVIN" }
    }
}

data class VenteInformasjon(
    val årsakTilSattPåVent: String?,
    val frist: LocalDate,
    val sattPåVentAv: String,
    val begrunnelse: String?
)