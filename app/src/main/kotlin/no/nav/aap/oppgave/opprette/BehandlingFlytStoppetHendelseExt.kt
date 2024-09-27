package no.nav.aap.oppgave.opprette

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import java.time.LocalDateTime

fun BehandlingFlytStoppetHendelse.hvemLøsteForrigeAvklaringsbehov(): String? {
    val avsluttedeAvklaringsbehov = avklaringsbehov
        .filter { it.status == Status.AVSLUTTET }
    val løsteForrigeAvklaringsbehov = avsluttedeAvklaringsbehov
        .map {it.endringer.sortedBy { it.tidsstempel }.last()}
        .sortedBy { it.tidsstempel }
        .lastOrNull()?.endretAv
    return løsteForrigeAvklaringsbehov
}

fun BehandlingFlytStoppetHendelse.opprettNyOppgave(avklaringsbehov: AvklaringsbehovHendelseDto, ident: String): OppgaveDto {
    return OppgaveDto(
        saksnummer = this.saksnummer.toString(),
        behandlingRef = this.referanse.referanse,
        behandlingOpprettet = this.opprettetTidspunkt,
        avklaringsbehovKode = AvklaringsbehovKode(avklaringsbehov.definisjon.type),
        opprettetAv = ident,
        opprettetTidspunkt = LocalDateTime.now()
    )
}

fun AvklaringsbehovHendelseDto.sistEndretAv(): String? {
    return endringer
        .sortedByDescending { it.tidsstempel }
        .filter { it.status == this.status }
        .map { it.endretAv }
        .firstOrNull()
}

private fun BehandlingFlytStoppetHendelse.gjenopprettOppgave(avklaringsbehov: AvklaringsbehovHendelseDto, ident: String): OppgaveDto {
    val oppgaveDto = this.opprettNyOppgave(avklaringsbehov, ident)
    val sistEndretAv = avklaringsbehov.sistEndretAv()
    oppgaveDto.copy(reservertAv = sistEndretAv, reservertTidspunkt = LocalDateTime.now())
    return oppgaveDto
}

