package no.nav.aap.oppgave.opprette

import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.opprett.AvklaringsbehovDto
import no.nav.aap.oppgave.opprett.Avklaringsbehovstatus
import no.nav.aap.oppgave.opprett.BehandlingshistorikkRequest
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import java.time.LocalDateTime
import java.util.UUID


fun BehandlingshistorikkRequest.lagOppgave(ident: String): OppgaveDto? {
    val åpentAvklaringsbehov = this.getÅpentAvklaringsbehov()
    if (åpentAvklaringsbehov == null) {
        return null
    }
    return when (åpentAvklaringsbehov.status) {
        Avklaringsbehovstatus.OPPRETTET ->
            this.opprettNyOppgave(åpentAvklaringsbehov, ident)
        Avklaringsbehovstatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER, Avklaringsbehovstatus.SENDT_TILBAKE_FRA_BESLUTTER ->
            this.gjenopprettOppgave(åpentAvklaringsbehov, ident)
        else -> return null
    }
}

fun BehandlingshistorikkRequest.hvemLøsteForrigeAvklaringsbehov(): String? {
    val avsluttedeAvklaringsbehov = avklaringsbehov
        .filter { it.status == Avklaringsbehovstatus.AVSLUTTET }
    val løsteForrigeAvklaringsbehov = avsluttedeAvklaringsbehov
        .map {it.endringer.sortedBy { it.tidsstempel }.last()}
        .sortedBy { it.tidsstempel }
        .lastOrNull()?.endretAv
    return løsteForrigeAvklaringsbehov
}

private fun BehandlingshistorikkRequest.opprettNyOppgave(avklaringsbehov: AvklaringsbehovDto, ident: String): OppgaveDto {
    return OppgaveDto(
        saksnummer = this.saksnummer,
        behandlingRef = UUID.fromString(this.referanse),
        behandlingOpprettet = this.opprettetTidspunkt,
        avklaringsbehovKode = AvklaringsbehovKode(avklaringsbehov.definisjon.type),
        opprettetAv = ident,
        opprettetTidspunkt = LocalDateTime.now()
    )
}

private fun BehandlingshistorikkRequest.gjenopprettOppgave(avklaringsbehov: AvklaringsbehovDto, ident: String): OppgaveDto {
    val oppgaveDto = this.opprettNyOppgave(avklaringsbehov, ident)
    val sistEndretAv = avklaringsbehov.sistEndretAv()
    oppgaveDto.copy(reservertAv = sistEndretAv, reservertTidspunkt = LocalDateTime.now())
    return oppgaveDto
}

private fun AvklaringsbehovDto.sistEndretAv(): String? {
    return endringer
        .sortedByDescending { it.tidsstempel }
        .filter { it.status == this.status }
        .map { it.endretAv }
        .firstOrNull()
}
