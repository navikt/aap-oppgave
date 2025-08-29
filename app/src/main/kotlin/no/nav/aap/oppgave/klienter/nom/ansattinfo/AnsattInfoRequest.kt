package no.nav.aap.oppgave.klienter.nom.ansattinfo


data class AnsattInfoRequest(val query: String, val ansattInfoVariables: AnsattInfoVariables)

data class AnsattInfoVariables(val navIdent: String)

