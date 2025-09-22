package no.nav.aap.oppgave.klienter.nom.ansattinfo


data class AnsattInfoRequest(val query: String, val variables: AnsattInfoVariables)

data class AnsattInfoVariables(val navIdent: String)

data class SøkRequest(val query: String, val variables: SøkVariables)

data class SøkVariables(val soketekst: String)

