package no.nav.aap.oppgave.klienter.nom.ansattinfo

import no.nav.aap.oppgave.klienter.graphql.GraphQLError

data class AnsattInfoRespons(
    val data: AnsattInfoData?,
    val errors: List<GraphQLError>?,
)

data class AnsattInfoData(
    val ressurs: NomRessurs?,
    val ressurser: List<NomBatchData>?,
)

data class NomRessurs(
    val visningsnavn: String,
)

data class NomBatchData(
    val id: String,
    val code: NomResultCode,
    val ressurs: NomBatchRessurs?,
)

data class NomBatchRessurs(
    val navident: String,
    val sluttdato: String?,
)

enum class NomResultCode {
    OK,
    ERROR,
    NOT_FOUND,
    NOT_IN_FILTER,
}