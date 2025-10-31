package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.MottattDokumentDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.mottattdokument.MottattDokument
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private val logger = LoggerFactory.getLogger(OppgaveOppdatering::class.java)

enum class BehandlingStatus {
    ÅPEN,
    LUKKET
}

enum class AvklaringsbehovStatus {
    OPPRETTET,
    AVSLUTTET,
    TOTRINNS_VURDERT,
    SENDT_TILBAKE_FRA_BESLUTTER,
    KVALITETSSIKRET,
    SENDT_TILBAKE_FRA_KVALITETSSIKRER,
    AVBRUTT;

    fun erÅpent(): Boolean {
        return this in setOf(
            OPPRETTET,
            SENDT_TILBAKE_FRA_BESLUTTER,
            SENDT_TILBAKE_FRA_KVALITETSSIKRER
        )
    }
}

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
    val reserverTil: String? = null,
    val relevanteIdenter: List<String> = emptyList(),
)

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

data class VenteInformasjon(
    val årsakTilSattPåVent: String?,
    val frist: LocalDate,
    val sattPåVentAv: String,
    val begrunnelse: String?
)


fun BehandlingFlytStoppetHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        personIdent = this.personIdent,
        saksnummer = this.saksnummer.toString(),
        referanse = this.referanse.referanse,
        vurderingsbehov = this.vurderingsbehov,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        årsakTilOpprettelse = this.årsakTilOpprettelse,
        behandlingstype = this.behandlingType.tilBehandlingstype(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForBehandlingsflyt(),
        reserverTil = this.reserverTil,
        relevanteIdenter = this.relevanteIdenterPåBehandling ?: emptyList(),
        venteInformasjon = if (this.erPåVent) {
            this.utledVenteInformasjon()
        } else null,
        mottattDokumenter = mottattDokumenter.tilMottattDokumenter(this.referanse.referanse),
    )
}

private fun BehandlingFlytStoppetHendelse.utledVenteInformasjon(): VenteInformasjon? {
    val ventebehov =
        this.avklaringsbehov.filter { it.avklaringsbehovDefinisjon.erVentebehov() && it.status.erÅpent() }
    if (ventebehov.size != 1) {
        logger.warn("Mer enn ett åpent ventebehov. Referanse: ${this.referanse.referanse}. Velger første.")
    }
    val førsteVentebehov = ventebehov.first()
    val siste = førsteVentebehov.endringer.tilEndringerForBehandlingsflyt().maxByOrNull { it.tidsstempel }!!
    if (siste.påVentTil == null) {
        logger.warn("Behandlingen er markert som påVent, men ventebehovet mangler frist. Behov $førsteVentebehov.")
        return null
    } else {
        // Her gjør vi noen antakelser om at åpne ventebehov alltid har årsak og frist.
        return VenteInformasjon(
            årsakTilSattPåVent = siste.påVentÅrsak,
            frist = siste.påVentTil,
            sattPåVentAv = siste.endretAv,
            begrunnelse = siste.begrunnelse.nullIfBlank()
        )
    }
}

private fun List<MottattDokumentDto>.tilMottattDokumenter(behandlingRef: UUID): List<MottattDokument> {
    return map {
        MottattDokument(
            type = it.type.name,
            behandlingRef = behandlingRef,
            referanse = it.referanse.verdi,
        )
    }
}

private fun TypeBehandling.tilBehandlingstype() =
    when (this) {
        TypeBehandling.Førstegangsbehandling -> Behandlingstype.FØRSTEGANGSBEHANDLING
        TypeBehandling.Revurdering -> Behandlingstype.REVURDERING
        TypeBehandling.Tilbakekreving -> Behandlingstype.TILBAKEKREVING
        TypeBehandling.Klage -> Behandlingstype.KLAGE
        TypeBehandling.SvarFraAndreinstans -> Behandlingstype.SVAR_FRA_ANDREINSTANS
        TypeBehandling.OppfølgingsBehandling -> Behandlingstype.OPPFØLGINGSBEHANDLING
        TypeBehandling.Aktivitetsplikt -> Behandlingstype.AKTIVITETSPLIKT
        TypeBehandling.Aktivitetsplikt11_9 -> Behandlingstype.AKTIVITETSPLIKT_11_9
    }

private fun List<AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelseForBehandlingsflyt(): List<AvklaringsbehovHendelse> {
    return this
        .filter { !it.avklaringsbehovDefinisjon.erVentebehov() }
        .map {
            AvklaringsbehovHendelse(
                avklaringsbehovKode = AvklaringsbehovKode(it.avklaringsbehovDefinisjon.kode.name),
                status = it.status.tilAvklaringsbehovStatus(),
                endringer = it.endringer.tilEndringerForBehandlingsflyt(),
            )
        }
}

private fun List<EndringDTO>.tilEndringerForBehandlingsflyt() =
    this.map {
        Endring(
            status = it.status.tilAvklaringsbehovStatus(),
            tidsstempel = it.tidsstempel,
            endretAv = it.endretAv,
            påVentTil = it.frist,
            påVentÅrsak = it.årsakTilSattPåVent?.name,
            begrunnelse = it.begrunnelse.nullIfBlank(),
            årsakTilRetur = it.årsakTilRetur.map { årsak -> ÅrsakTilReturKode.valueOf(årsak.årsak.name) }
        )
    }

private fun String?.nullIfBlank() = if (this.isNullOrBlank()) null else this


private fun no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.tilBehandlingsstatus(): BehandlingStatus {
    val erAvsluttet = this == no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.AVSLUTTET

    if (erAvsluttet) {
        return BehandlingStatus.LUKKET
    }
    return BehandlingStatus.ÅPEN
}

private fun no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.tilAvklaringsbehovStatus(): AvklaringsbehovStatus {
    return when (this) {
        no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET -> AvklaringsbehovStatus.OPPRETTET
        no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET -> AvklaringsbehovStatus.AVSLUTTET
        no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.TOTRINNS_VURDERT -> AvklaringsbehovStatus.TOTRINNS_VURDERT
        no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_BESLUTTER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
        no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.KVALITETSSIKRET -> AvklaringsbehovStatus.KVALITETSSIKRET
        no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER
        no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVBRUTT -> AvklaringsbehovStatus.AVBRUTT
    }
}

fun DokumentflytStoppetHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        personIdent = this.ident,
        saksnummer = this.saksnummer,
        referanse = this.referanse,
        journalpostId = this.journalpostId.referanse,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        behandlingstype = this.behandlingType.tilBehandlingstype(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForPostmottak(),
        vurderingsbehov = emptyList(),
        mottattDokumenter = emptyList(),
        årsakTilOpprettelse = null,
        venteInformasjon = this.utledVenteinformasjonFraPostmottak()
    )
}

private fun DokumentflytStoppetHendelse.utledVenteinformasjonFraPostmottak(): VenteInformasjon? {
    val åpentVentebehov = this.avklaringsbehov.filter {
        it.avklaringsbehovDefinisjon.erVentebehov() && it.status.tilAvklaringsbehovStatus().erÅpent()
    }

    if (åpentVentebehov.isEmpty()) {
        return null
    }

    if (åpentVentebehov.size > 1) {
        logger.warn("Mer enn ett åpent ventebehov. Referanse: ${this.referanse}. Velger første.")
    }

    val førsteVentebehov = åpentVentebehov.first()
    val sisteEndring = førsteVentebehov.endringer.tilEndringerForPostmottak().maxByOrNull { it.tidsstempel }

    if (sisteEndring?.påVentTil != null) {
        return VenteInformasjon(
            årsakTilSattPåVent = sisteEndring.påVentÅrsak,
            frist = sisteEndring.påVentTil,
            sattPåVentAv = sisteEndring.endretAv,
            begrunnelse = sisteEndring.begrunnelse.nullIfBlank()
        )
    }
    return null
}

private fun no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling.tilBehandlingstype() =
    when (this) {
        no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling.DokumentHåndtering -> Behandlingstype.DOKUMENT_HÅNDTERING
        no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling.Journalføring -> Behandlingstype.JOURNALFØRING
    }


private fun no.nav.aap.postmottak.kontrakt.behandling.Status.tilBehandlingsstatus(): BehandlingStatus {
    if (this.erAvsluttet()) {
        return BehandlingStatus.LUKKET
    }
    return BehandlingStatus.ÅPEN
}

private fun List<no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelseForPostmottak(): List<AvklaringsbehovHendelse> {
    return this
        .filter { !it.avklaringsbehovDefinisjon.erVentebehov() }
        .map {
            AvklaringsbehovHendelse(
                avklaringsbehovKode = AvklaringsbehovKode(it.avklaringsbehovDefinisjon.kode.name),
                status = it.status.tilAvklaringsbehovStatus(),
                endringer = it.endringer.tilEndringerForPostmottak(),
            )
        }
}

private fun Status.tilAvklaringsbehovStatus(): AvklaringsbehovStatus {
    return when (this) {
        Status.OPPRETTET -> AvklaringsbehovStatus.OPPRETTET
        Status.AVSLUTTET -> AvklaringsbehovStatus.AVSLUTTET
        Status.SENDT_TILBAKE_FRA_BESLUTTER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
        Status.AVBRUTT -> AvklaringsbehovStatus.AVBRUTT
    }
}

private fun List<no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO>.tilEndringerForPostmottak() =
    this.map {
        Endring(
            status = it.status.tilAvklaringsbehovStatus(),
            tidsstempel = it.tidsstempel,
            endretAv = it.endretAv,
            påVentTil = it.frist,
            påVentÅrsak = it.årsakTilSattPåVent?.name,
            begrunnelse = it.begrunnelse
        )
    }