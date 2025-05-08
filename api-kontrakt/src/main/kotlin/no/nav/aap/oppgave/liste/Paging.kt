package no.nav.aap.oppgave.liste

data class Paging(
    val side: Int = 1,
    val antallPerSide: Int = 10
)