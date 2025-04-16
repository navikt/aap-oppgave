package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

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
    AVBRUTT
}

data class OppgaveOppdatering(
    val personIdent: String? = null,
    val saksnummer: String? = null,
    val referanse: UUID? = null,
    val journalpostId: Long? = null,
    val behandlingStatus: BehandlingStatus,
    val behandlingstype: Behandlingstype,
    val opprettetTidspunkt: LocalDateTime,
    val avklaringsbehov: List<AvklaringsbehovHendelse>
)

data class AvklaringsbehovHendelse(
    val avklaringsbehovKode: AvklaringsbehovKode,
    val status: AvklaringsbehovStatus,
    val endringer: List<Endring>
)

data class Endring(
    val status: AvklaringsbehovStatus,
    val tidsstempel: LocalDateTime = LocalDateTime.now(),
    val endretAv: String,
    val påVentTil: LocalDate?,
    val påVentÅrsak: String?
)

fun BehandlingFlytStoppetHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        personIdent = this.personIdent,
        saksnummer = this.saksnummer.toString(),
        referanse = this.referanse.referanse,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        behandlingstype = this.behandlingType.tilBehandlingstype(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForBehandlingsflyt()
    )
}

private fun TypeBehandling.tilBehandlingstype() =
    when (this) {
        TypeBehandling.Førstegangsbehandling -> Behandlingstype.FØRSTEGANGSBEHANDLING
        TypeBehandling.Revurdering -> Behandlingstype.REVURDERING
        TypeBehandling.Tilbakekreving -> Behandlingstype.TILBAKEKREVING
        TypeBehandling.Klage -> Behandlingstype.KLAGE
    }

private fun List<AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelseForBehandlingsflyt(): List<AvklaringsbehovHendelse> {
    return this
        .filter { !it.avklaringsbehovDefinisjon.erVentebehov() }
        .map {
            AvklaringsbehovHendelse(
                avklaringsbehovKode = AvklaringsbehovKode(it.avklaringsbehovDefinisjon.kode.name),
                status = it.status.tilAvklaringsbehovStatus(),
                endringer = it.endringer.tilEndringerForBehandlingsflyt()
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
            påVentÅrsak = it.årsakTilSattPåVent?.name
        )
    }

private fun no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.tilBehandlingsstatus(): BehandlingStatus {
    if (this.erAvsluttet()) {
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
        referanse = this.referanse,
        journalpostId = this.journalpostId.referanse,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        behandlingstype = this.behandlingType.tilBehandlingstype(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForPostmottak()
    )
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
    return this.map {
        AvklaringsbehovHendelse(
            avklaringsbehovKode = AvklaringsbehovKode(it.avklaringsbehovDefinisjon.kode.name),
            status = it.status.tilAvklaringsbehovStatus(),
            endringer = it.endringer.tilEndringerForPostmottak()
        )
    }
}

private fun Status.tilAvklaringsbehovStatus(): AvklaringsbehovStatus {
    return when (this) {
        Status.OPPRETTET -> AvklaringsbehovStatus.OPPRETTET
        Status.AVSLUTTET -> AvklaringsbehovStatus.AVSLUTTET
        Status.SENDT_TILBAKE_FRA_BESLUTTER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
        Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER
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
            påVentÅrsak = null
        )
    }