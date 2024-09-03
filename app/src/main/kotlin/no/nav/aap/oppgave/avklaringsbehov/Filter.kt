package no.nav.aap.oppgave.avklaringsbehov

data class Filter(
    val sakstype: Sakstype,
    val avklaresAv: AvklaresAv,
    val navkontor: Navkontor? = null,
    val avklaringsbehovTyper: Set<AvklaringsbehovType>? = null,

) {


    fun whereClause(): String {
        TODO()
    }


}