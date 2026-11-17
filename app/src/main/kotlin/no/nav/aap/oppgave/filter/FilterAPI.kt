package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentFilterApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) {

    route("/filter").get<FilterRequestDto, List<FilterResponse>> { req ->
        prometheus.httpCallCounter("/filter").increment()
        val filterListe = dataSource.transaction(readOnly = true) { connection ->
            FilterRepository(connection).hentForEnheter(req.enheter)
        }.skjulSykepengerKøHvisToggleAv(UnleashServiceProvider.provideUnleashService())
            .map { it.tilResponse() }
        respond(filterListe)
    }
}

internal fun List<Filter>.skjulSykepengerKøHvisToggleAv(unleash: IUnleashService): List<Filter> =
    if (unleash.isEnabled(FeatureToggles.OppgavekoSykepenger)) this else filterNot { it.kunMedGjenståendeSykedager }
