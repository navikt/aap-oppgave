package no.nav.aap.oppgave.plukk

data class FinnNesteOppgaveDto(
    val filterId: Long,
    val enheter: Set<String> = setOf(),
)
