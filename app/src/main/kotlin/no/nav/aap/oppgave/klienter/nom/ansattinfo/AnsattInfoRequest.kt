package no.nav.aap.oppgave.klienter.nom.ansattinfo


data class AnsattInfoRequest(val query: String, val variables: AnsattInfoVariables)

data class AnsattInfoVariables(val navIdent: String)

data class AnsattSøkRequest(val query: String, val variables: AnsattSøkVariables)

data class AnsattSøkVariables(val soketekst: String)

