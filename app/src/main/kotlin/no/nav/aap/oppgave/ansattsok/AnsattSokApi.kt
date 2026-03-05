package no.nav.aap.oppgave.ansattsok

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomRessursAnsattSøk
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.unleash.FeatureToggles


data class AnsattSokRequest (
    val søketekst: String
)

/**
 * Søk etter saksbehandlere med navn for å få både navn og navident
 */
fun NormalOpenAPIRoute.ansattSokApi(
    prometheus: PrometheusMeterRegistry
) {
    route("/ansatt-sok").post<Unit, List<NomRessursAnsattSøk>, AnsattSokRequest> { _, request ->
        prometheus.httpCallCounter("/ansatt-sok").increment()

        val søkeResultat = AnsattSokService().ansattSok(request.søketekst)
        respond(søkeResultat)
    }
}
