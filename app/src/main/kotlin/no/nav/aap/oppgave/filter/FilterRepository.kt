package no.nav.aap.oppgave.filter

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.opprett.AvklaringsbehovKode

// Forløpig mock repository
class FilterRepository(private val connection: DBConnection) {

    private val filterMap = mapOf(
        1L to Filter(1L, "Alle oppgaver"),
        2L to Filter(2L, "Oppgaver av med avklaringsbehov 1000", setOf(AvklaringsbehovKode("1000"))),
    )

    fun hentFilter(filterId: Long): Filter? {
        return filterMap[filterId]
    }

    fun hentAlleFilter(): List<Filter> {
        return filterMap.values.toList()
    }

}