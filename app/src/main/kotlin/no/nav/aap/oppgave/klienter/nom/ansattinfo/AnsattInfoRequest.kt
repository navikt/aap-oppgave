package no.nav.aap.oppgave.klienter.nom.ansattinfo


data class AnsattInfoRequest(val query: String, val variables: AnsattInfoVariables)

data class AnsattInfoVariables(val navIdent: String)

data class AnsattInfoBatchRequest(
    val query: String,
    val variables: AnsattInfoBatchVariables
)

data class AnsattInfoBatchVariables(val navidenter: List<String>)