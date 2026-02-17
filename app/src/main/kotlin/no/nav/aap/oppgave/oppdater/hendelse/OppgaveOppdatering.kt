package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.oppgave.mottattdokument.MottattDokument
import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


const val KELVIN = "Kelvin"

/**
 * @param reserverTil Hvis ikke-null, reserver til denne personen.
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
    val tattAvVentAutomatisk: Boolean = false,
    val reserverTil: String? = null,
    val relevanteIdenter: List<String> = emptyList(),
    val totaltFeilutbetaltBeløp : BigDecimal? = null,
    val tilbakekrevingsUrl : String? = null,
)

data class VenteInformasjon(
    val årsakTilSattPåVent: String?,
    val frist: LocalDate,
    val sattPåVentAv: String,
    val begrunnelse: String?
)