package no.nav.aap.oppgave.avklaringsbehov

data class Filter(
    val behandlingType: BehandlingType,
    val avklaresAv: AvklaresAv,
    val navkontor: Navkontor? = null,
    val avklaringsbehovTyper: Set<AvklaringsbehovType> = emptySet()
)