package no.nav.aap.oppgave.klienter.nom.ansattinfo


data class AnsattInfoRequest(val query: String, val variables: AnsattInfoVariables)

data class SearchAnsattRequest(val query: String, val variables: SearchAnsattVariables)

data class AnsattInfoVariables(val navIdent: String)

data class SearchAnsattVariables(val searchTerm: String)
