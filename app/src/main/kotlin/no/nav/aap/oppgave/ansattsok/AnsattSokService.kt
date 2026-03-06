package no.nav.aap.oppgave.ansattsok

import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiGateway
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomRessursAnsattSøk
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider

class AnsattSokService (
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService()
) {
    private val ansattInfoGateway = NomApiGateway.withClientCredentialsRestClient()

    fun ansattSok(searchTerm: String): List<NomRessursAnsattSøk> {
        if (unleashService.isEnabled(FeatureToggles.AnsattSok)) {
            return ansattInfoGateway.ansattSøk(searchTerm)
        } else {
            return emptyList()
        }
    }
}