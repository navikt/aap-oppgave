package no.nav.aap.oppgave.liste

data class OppgavelisteRequest(
    val filterId: Long,
    val enheter: Set<String> = emptySet(),
    val maxAntall: Int = 10,
)
