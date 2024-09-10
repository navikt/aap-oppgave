package no.nav.aap.oppgave.avklaringsbehov

data class Filter(
    val avklaringsbehovKoder: Set<AvklaringsbehovKode> = emptySet()
)