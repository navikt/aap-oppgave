package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.MottattDokumentDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.mottattdokument.MottattDokument
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger(OppgaveOppdatering::class.java)

fun BehandlingFlytStoppetHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        personIdent = this.personIdent,
        saksnummer = this.saksnummer.toString(),
        referanse = this.referanse.referanse,
        vurderingsbehov = this.vurderingsbehov,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        årsakTilOpprettelse = this.årsakTilOpprettelse.name,
        behandlingstype = this.behandlingType.tilBehandlingstype(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForBehandlingsflytUtenVentebehov(this.saksnummer),
        reserverTil = this.reserverTil,
        relevanteIdenter = this.relevanteIdenterPåBehandling ?: emptyList(),
        venteInformasjon = if (this.erPåVent) {
            this.utledVenteInformasjon()
        } else null,
        tattAvVentAutomatisk = !this.erPåVent && this.avklaringsbehov.filter { it.avklaringsbehovDefinisjon.erVentebehov() }
            .tilAvklaringsbehovHendelseForBehandlingsflyt().kelvinTokBehandlingAvVent(),
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
            begrunnelse = siste.begrunnelse
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

private fun List<AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelseForBehandlingsflytUtenVentebehov(saksnummer: Saksnummer): List<AvklaringsbehovHendelse> {
    val avklaringsbehovUtenVentebehov = this
        .filter { !it.avklaringsbehovDefinisjon.erVentebehov() }
        .tilAvklaringsbehovHendelseForBehandlingsflyt()
    if (avklaringsbehovUtenVentebehov.isEmpty() && this.isNotEmpty() && !this.erOppfølgingsbehandlingPåVent()) {
        // når det bare er ventebehov opprettes ikke oppgave, og behandlingen blir borte fra oppgavelista
        // unntaket er å vente på frist på oppfølgingsbehandling, den skal ikke opprette oppgave.
        logger.error("Mottok hendelse fra behandlingsflyt med bare ventebehov: ${this.map { it.avklaringsbehovDefinisjon.name }} på sak $saksnummer")
    }
    return avklaringsbehovUtenVentebehov
}

private fun List<AvklaringsbehovHendelseDto>.erOppfølgingsbehandlingPåVent(): Boolean {
    return this.map { it.avklaringsbehovDefinisjon } == listOf(Definisjon.VENT_PÅ_OPPFØLGING)
}

private fun List<AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelseForBehandlingsflyt(): List<AvklaringsbehovHendelse> {
    return this.map {
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
            begrunnelse = it.begrunnelse,
            årsakTilRetur = it.årsakTilRetur.map { årsak -> ÅrsakTilReturKode.valueOf(årsak.årsak.name) }
        )
    }

fun String?.nullIfBlank() = if (this.isNullOrBlank()) null else this

private fun no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.tilBehandlingsstatus(): BehandlingStatus {
    val erAvsluttet = this == no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.AVSLUTTET

    if (erAvsluttet) {
        return BehandlingStatus.LUKKET
    }
    return BehandlingStatus.ÅPEN
}

fun no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.tilAvklaringsbehovStatus(): AvklaringsbehovStatus {
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
