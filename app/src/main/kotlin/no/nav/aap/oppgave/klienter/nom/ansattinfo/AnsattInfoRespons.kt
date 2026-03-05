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

data class AnsattSøkRespons(
    val data: AnsattSøkData?,
    val errors: List<GraphQLError>?,
)
data class AnsattSøkData(
    val searchRessurs: List<NomRessursAnsattSøk>,
)
data class NomRessursAnsattSøk(
    val visningsnavn: String,
    val navident: String,
)