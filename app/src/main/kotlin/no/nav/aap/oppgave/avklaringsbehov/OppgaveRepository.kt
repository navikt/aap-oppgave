package no.nav.aap.oppgave.avklaringsbehov

import no.nav.aap.komponenter.dbconnect.DBConnection

class OppgaveRepository(private val connection: DBConnection) {

    fun opprettOppgave(oppgave: Oppgave): OppgaveId {
        TODO()
    }

    fun avsluttOppgave(oppgaveId: OppgaveId) {
        TODO()
    }

    fun reserverNesteOppgave(filter: Filter): Oppgave {
        TODO()
    }

    fun hentMineOppgaver(ident: String): List<Oppgave> {
        TODO()
    }



}