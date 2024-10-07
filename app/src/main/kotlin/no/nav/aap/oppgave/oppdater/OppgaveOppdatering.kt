package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
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
    val saksnummer: String? = null,
    val referanse: UUID? = null,
    val journalpostId: Long? = null,
    val behandlingStatus: BehandlingStatus,
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
    val endretAv: String
)

fun BehandlingFlytStoppetHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        saksnummer = this.saksnummer.toString(),
        referanse = this.referanse.referanse,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForBehandlingsflyt()
    )
}

private fun List<AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelseForBehandlingsflyt(): List<AvklaringsbehovHendelse> {
    return this.map {
        AvklaringsbehovHendelse(
            avklaringsbehovKode = AvklaringsbehovKode(it.definisjon.type),
            status = it.status.tilAvklaringsbehovStatus(),
            endringer = it.endringer.tilEndringerForBehandlingsflyt()
        )
    }
}

private fun List<EndringDTO>.tilEndringerForBehandlingsflyt() =
    this.map { Endring(status = it.status.tilAvklaringsbehovStatus(), tidsstempel = it.tidsstempel, endretAv = it.endretAv) }

private fun no.nav.aap.behandlingsflyt.kontrakt.behandling.Status.tilBehandlingsstatus(): BehandlingStatus {
    if (this.erAvsluttet()) {
        return BehandlingStatus.LUKKET
    }
    return BehandlingStatus.ÅPEN
}

private fun no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.tilAvklaringsbehovStatus(): AvklaringsbehovStatus {
    return AvklaringsbehovStatus.valueOf(this.name)
}

fun DokumentflytStoppetHendelse.tilOppgaveOppdatering(): OppgaveOppdatering {
    return OppgaveOppdatering(
        journalpostId = this.referanse.referanse,
        behandlingStatus = this.status.tilBehandlingsstatus(),
        opprettetTidspunkt = this.opprettetTidspunkt,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelseForPostmottak()
    )
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
            avklaringsbehovKode = AvklaringsbehovKode(it.definisjon.type),
            status = it.status.tilAvklaringsbehovStatus(),
            endringer = it.endringer.tilEndringerForPostmottak()
        )
    }
}

private fun no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.tilAvklaringsbehovStatus(): AvklaringsbehovStatus {
    return AvklaringsbehovStatus.valueOf(this.name)
}

private fun List<no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO>.tilEndringerForPostmottak() =
    this.map { Endring(status = it.status.tilAvklaringsbehovStatus(), tidsstempel = it.tidsstempel, endretAv = it.endretAv) }