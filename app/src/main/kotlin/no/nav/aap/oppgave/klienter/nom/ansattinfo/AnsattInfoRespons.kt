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