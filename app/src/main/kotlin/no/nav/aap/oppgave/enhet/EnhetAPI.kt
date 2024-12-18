package no.nav.aap.oppgave.enhet

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.norg.NorgKlient
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident


fun NormalOpenAPIRoute.hentEnhetApi(msGraphClient: IMsGraphClient, prometheus: PrometheusMeterRegistry) =

    route("/enheter").get<Unit, List<EnhetDto>> {
        prometheus.httpCallCounter("/enheter").increment()
        val enheter = EnhetService(msGraphClient).hentEnheter(token().token(), ident())
        val enhetNrTilNavn = NorgKlient().hentEnheter()
        val enheterMedNavn = enheter.map { EnhetDto(it, enhetNrTilNavn[it] ?: "") }
        respond(enheterMedNavn)
    }