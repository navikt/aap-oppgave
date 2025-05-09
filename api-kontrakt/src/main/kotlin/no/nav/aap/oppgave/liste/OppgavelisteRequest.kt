package no.nav.aap.oppgave.liste

data class OppgavelisteRequest(
    val filterId: Long,
    val enheter: Set<String> = emptySet(),
    val veileder: Boolean = false,
    val paging: Paging
)
