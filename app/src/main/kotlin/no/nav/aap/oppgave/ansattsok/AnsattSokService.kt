package no.nav.aap.oppgave.ansattsok

import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiGateway
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomRessursAnsattSøk

class AnsattSokService () {
    private val ansattInfoGateway = NomApiGateway.withClientCredentialsRestClient()

    fun ansattSok(searchTerm: String): List<NomRessursAnsattSøk> {
        return ansattInfoGateway.ansattSøk(searchTerm)
    }
}