package no.nav.aap.oppgave.filter

data class OppgaveSøkDto(
    val filterId: Long,
    val enheter: Set<String> = emptySet(),
)
