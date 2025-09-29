package no.nav.aap.oppgave.tildel

data class TildelOppgaveRequest (
    val oppgaver: List<Long>,
    val saksbehandlerIdent: String
)