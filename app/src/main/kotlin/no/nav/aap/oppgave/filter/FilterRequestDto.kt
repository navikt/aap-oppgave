package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.annotations.parameters.QueryParam

data class FilterRequestDto(
    @param:QueryParam("Enhetsfilter") val enheter: List<String>?
)