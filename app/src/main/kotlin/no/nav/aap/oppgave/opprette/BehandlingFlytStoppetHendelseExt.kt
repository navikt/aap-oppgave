package no.nav.aap.oppgave.opprette

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import java.time.LocalDateTime

fun BehandlingFlytStoppetHendelse.lagOppgave(ident: String): OppgaveDto? {
    val åpentAvklaringsbehov = this.finnÅpentAvklaringsbehov()
    if (åpentAvklaringsbehov == null) {
        return null
    }
    return when (åpentAvklaringsbehov.status) {
        Status.OPPRETTET ->
            this.opprettNyOppgave(åpentAvklaringsbehov, ident)
        Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER, Status.SENDT_TILBAKE_FRA_BESLUTTER ->
            this.gjenopprettOppgave(åpentAvklaringsbehov, ident)
        else -> return null
    }
}

fun BehandlingFlytStoppetHendelse.hvemLøsteForrigeAvklaringsbehov(): String? {
    val avsluttedeAvklaringsbehov = avklaringsbehov
        .filter { it.status == Status.AVSLUTTET }
    val løsteForrigeAvklaringsbehov = avsluttedeAvklaringsbehov
        .map {it.endringer.sortedBy { it.tidsstempel }.last()}
        .sortedBy { it.tidsstempel }
        .lastOrNull()?.endretAv
    return løsteForrigeAvklaringsbehov
}

private fun BehandlingFlytStoppetHendelse.opprettNyOppgave(avklaringsbehov: AvklaringsbehovHendelseDto, ident: String): OppgaveDto {
    return OppgaveDto(
        saksnummer = this.saksnummer.toString(),
        behandlingRef = this.referanse.referanse,
        behandlingOpprettet = this.opprettetTidspunkt,
        avklaringsbehovKode = AvklaringsbehovKode(avklaringsbehov.definisjon.type),
        opprettetAv = ident,
        opprettetTidspunkt = LocalDateTime.now()
    )
}

private fun BehandlingFlytStoppetHendelse.gjenopprettOppgave(avklaringsbehov: AvklaringsbehovHendelseDto, ident: String): OppgaveDto {
    val oppgaveDto = this.opprettNyOppgave(avklaringsbehov, ident)
    val sistEndretAv = avklaringsbehov.sistEndretAv()
    oppgaveDto.copy(reservertAv = sistEndretAv, reservertTidspunkt = LocalDateTime.now())
    return oppgaveDto
}

private fun AvklaringsbehovHendelseDto.sistEndretAv(): String? {
    return endringer
        .sortedByDescending { it.tidsstempel }
        .filter { it.status == this.status }
        .map { it.endretAv }
        .firstOrNull()
}

fun BehandlingFlytStoppetHendelse.finnÅpentAvklaringsbehov() = avklaringsbehov.firstOrNull {
    it.status in setOf(
        Status.OPPRETTET,
        Status.SENDT_TILBAKE_FRA_BESLUTTER,
        Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER
    )
}