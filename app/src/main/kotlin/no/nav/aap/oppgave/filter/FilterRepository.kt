package no.nav.aap.oppgave.filter

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode

// Forløpig mock repository
class FilterRepository(private val connection: DBConnection) {

    private val filterDtoMap = mapOf(
        1L to FilterDto(1L, "Alle oppgaver"),
        2L to FilterDto(2L, "Oppgaver av med avklaringsbehov 1000", setOf(AvklaringsbehovKode("1000"))),
    )

    fun hentFilter(filterId: Long): FilterDto? {
        return filterDtoMap[filterId]
    }

    fun hentAlleFilter(): List<FilterDto> {
        return filterDtoMap.values.toList()
    }

}