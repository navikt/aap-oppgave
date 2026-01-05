package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OppgaveOppdatering::class.java)

fun DokumentflytStoppetHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        personIdent = this.ident,
        saksnummer = this.saksnummer,
        referanse = this.referanse,
        journalpostId = this.journalpostId.referanse,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        behandlingstype = this.behandlingType.tilBehandlingstype(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForPostmottakUtenVentebehov(),
        vurderingsbehov = emptyList(),
        mottattDokumenter = emptyList(),
        årsakTilOpprettelse = null,
        venteInformasjon = this.utledVenteinformasjonFraPostmottak(),
        tattAvVentAutomatisk = this.avklaringsbehov.filter { it.avklaringsbehovDefinisjon.erVentebehov() }
            .tilAvklaringsbehovHendelse().kelvinTokBehandlingAvVent(),
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

private fun List<no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelseForPostmottakUtenVentebehov(): List<AvklaringsbehovHendelse> {
    return this
        .filter { !it.avklaringsbehovDefinisjon.erVentebehov() }
        .tilAvklaringsbehovHendelse()
}

private fun List<no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelse(): List<AvklaringsbehovHendelse> {
    return this.map {
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