package no.nav.aap.oppgave.klienter.pdl

import no.nav.aap.postmottak.klient.graphql.GraphQLError
import no.nav.aap.postmottak.klient.graphql.GraphQLExtensions

internal data class PdlResponse(
    val data: PdlData?,
    val errors: List<GraphQLError>?,
    val extensions: GraphQLExtensions?
)

data class PdlData(
    val hentGeografiskTilknytning: GeografiskTilknytning? = null
)


data class GeografiskTilknytning(
    val gtType: GeografiskTilknytningType,
    val gtKommune: String? = null,
    val gtBydel: String? = null,
    val gtLand: String? = null,
)

enum class GeografiskTilknytningType{
    KOMMUNE,
    BYDEL,
    UTLAND,
    UDEFINERT
}


