package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.annotations.parameters.PathParam

data class FilterId(@PathParam("filterId") val filterId: Long)
