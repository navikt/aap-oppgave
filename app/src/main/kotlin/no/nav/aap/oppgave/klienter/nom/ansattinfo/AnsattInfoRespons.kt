package no.nav.aap.oppgave.klienter.nom.ansattinfo

import no.nav.aap.oppgave.klienter.graphql.GraphQLError

data class AnsattInfoRespons(
    val data: AnsattInfoData?,
    val errors: List<GraphQLError>?,
)

data class AnsattInfoData(
    val ressurs: NomRessurs?,
)

data class NomRessurs(
    val visningsnavn: String,
)

data class SøkRespons(
    val data: SøkData?,
    val errors: List<GraphQLError>?,
)

data class SøkData(
    val search: List<AnsattFraSøk>
)

data class AnsattFraSøk(
    val visningsnavn: String,
    val navident: String,
    val orgTilknytning: List<OrgEnhet>
)

data class OrgEnhet(
    val orgEnhetsType: OrgEnhetsType,
)

enum class OrgEnhetsType {
    NAV_KONTOR,
    NAV_ARBEID_OG_YTELSER
}