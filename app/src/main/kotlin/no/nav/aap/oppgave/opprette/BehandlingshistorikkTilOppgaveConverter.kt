package no.nav.aap.oppgave.opprette

import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.opprett.AvklaringsbehovDto
import no.nav.aap.oppgave.opprett.Avklaringsbehovstatus
import no.nav.aap.oppgave.opprett.BehandlingshistorikkRequest
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import java.time.LocalDateTime
import java.util.UUID

object BehandlingshistorikkTilOppgaveConverter {

    fun lagOppgave(behandlingshistorikk: BehandlingshistorikkRequest, ident: String): OppgaveDto? {
        val åpentAvklaringsbehov = behandlingshistorikk.getÅpentAvklaringsbehov()
        if (åpentAvklaringsbehov == null) {
            return null
        }
        return when (åpentAvklaringsbehov.status) {
            Avklaringsbehovstatus.OPPRETTET ->
                opprettNyOppgave(behandlingshistorikk, åpentAvklaringsbehov, ident)
            Avklaringsbehovstatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER, Avklaringsbehovstatus.SENDT_TILBAKE_FRA_BESLUTTER ->
                gjenopprettOppgave(behandlingshistorikk, åpentAvklaringsbehov, ident)
            else -> return null
        }
    }

    private fun opprettNyOppgave(behandlingshistorikkRequest: BehandlingshistorikkRequest,
                                 avklaringsbehov: AvklaringsbehovDto,
                                 ident: String
    ): OppgaveDto {
        return OppgaveDto(
            saksnummer = behandlingshistorikkRequest.saksnummer,
            behandlingRef = UUID.fromString(behandlingshistorikkRequest.referanse),
            behandlingOpprettet = behandlingshistorikkRequest.opprettetTidspunkt,
            avklaringsbehovKode = AvklaringsbehovKode(avklaringsbehov.definisjon.type),
            opprettetAv = ident,
            opprettetTidspunkt = LocalDateTime.now()
        )
    }

    private fun gjenopprettOppgave(behandlingshistorikkRequest: BehandlingshistorikkRequest,
                                   avklaringsbehov: AvklaringsbehovDto,
                                   ident: String
    ): OppgaveDto? {
        val oppgaveDto = opprettNyOppgave(behandlingshistorikkRequest, avklaringsbehov, ident)
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

}