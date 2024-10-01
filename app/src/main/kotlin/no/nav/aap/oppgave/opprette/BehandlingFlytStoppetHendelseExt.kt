package no.nav.aap.oppgave.opprette

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import java.time.LocalDateTime

fun BehandlingFlytStoppetHendelse.hvemLøsteForrigeAvklaringsbehov(): Pair<AvklaringsbehovKode, String>? {
    val sisteAvsluttetAvklaringsbehov = avklaringsbehov
        .filter { it.status == Status.AVSLUTTET }
        .filter {it.sistEndretAv() != null}
        .sortedBy { it.sistEndret() }
        .lastOrNull()

    if (sisteAvsluttetAvklaringsbehov == null) {
        return null
    }
    return Pair(AvklaringsbehovKode(sisteAvsluttetAvklaringsbehov.definisjon.type), sisteAvsluttetAvklaringsbehov.sistEndretAv()!!)
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
        .sortedBy { it.tidsstempel }
        .filter { it.status == this.status }
        .map { it.endretAv }
        .lastOrNull()
}

fun AvklaringsbehovHendelseDto.sistEndret(): LocalDateTime {
    return endringer
        .sortedBy { it.tidsstempel }
        .filter { it.status == this.status }
        .map { it.tidsstempel }
        .last()
}

