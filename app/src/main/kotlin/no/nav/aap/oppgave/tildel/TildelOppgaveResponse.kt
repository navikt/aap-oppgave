package no.nav.aap.oppgave.tildel

data class TildelOppgaveResponse(
    val oppgaver: List<Long>,
    val tildeltTilSaksbehandler: String,
)