package no.nav.aap.oppgave.opprette

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveRepository

class OppdaterOppgaveService(private val connection: DBConnection) {

    fun oppdaterOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse): AvklaringsbehovReferanseDto? {
        val oppgave = behandlingFlytStoppetHendelse.lagOppgave("Kelvin")
        if (oppgave != null) {
            val avklaringsbehovReferanse = oppgave.tilAvklaringsbehovReferanseDto()
            val oppgaveRepo = OppgaveRepository(connection)
            val eksisterendeOppgave = oppgaveRepo.hentOppgave(avklaringsbehovReferanse)
            if (eksisterendeOppgave != null) {
                oppgaveRepo.gjenåpneOppgave(eksisterendeOppgave.id!!, "Kelvin")
            } else {
                oppgaveRepo.opprettOppgave(oppgave)
            }
            return avklaringsbehovReferanse
        } else {
            return null
        }
    }

}