package no.nav.aap.oppgave.tildel

data class SaksbehandlerSøkRequest(
    val søketekst: String,
    val oppgaver: List<Long>,
)
