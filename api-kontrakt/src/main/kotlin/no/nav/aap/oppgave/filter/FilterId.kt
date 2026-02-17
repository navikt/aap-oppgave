package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.annotations.parameters.PathParam

data class FilterId(@param:PathParam("filterId") val filterId: Long)
