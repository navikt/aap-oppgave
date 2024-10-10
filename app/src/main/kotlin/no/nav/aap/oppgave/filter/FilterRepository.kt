package no.nav.aap.oppgave.filter

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon

// Forløpig mock repository
class FilterRepository(private val connection: DBConnection) {

    private val filterDtoMap = mapOf(
        1L to FilterDto(1L, "Alle oppgaver"),
        2L to FilterDto(2L, "Alle postmottak oppgaver",
            avklaringsbehovKoder = Definisjon.entries.map {it.kode}.toSet()),
        3L to FilterDto(3L, "Alle behandlingsflyt oppgaver",
            avklaringsbehovKoder = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.entries.map {it.kode}.toSet()),
        4L to FilterDto(4L, "Alle førstegangsbehandling oppgaver",
            behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING)),
        5L to FilterDto(5L, "Alle revurdering oppgaver",
            behandlingstyper = setOf(Behandlingstype.REVURDERING)),
        6L to FilterDto(6L, "Alle tilbakekreving oppgaver",
            behandlingstyper = setOf(Behandlingstype.TILBAKEKREVING)),
        7L to FilterDto(7L, "Alle klage oppgaver",
            behandlingstyper = setOf(Behandlingstype.KLAGE)),
        8L to FilterDto(8L, "Alle dokumenthåndtering oppgaver",
            behandlingstyper = setOf(Behandlingstype.DOKUMENT_HÅNDTERING)),
    )

    fun hentFilter(filterId: Long): FilterDto? {
        return filterDtoMap[filterId]
    }

    fun hentAlleFilter(): List<FilterDto> {
        return filterDtoMap.values.toList()
    }

}