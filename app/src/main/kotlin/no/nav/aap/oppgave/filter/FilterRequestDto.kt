package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.annotations.parameters.QueryParam

data class FilterRequestDto(
    @QueryParam("Enhetsfilter") val enheter: List<String>?
)