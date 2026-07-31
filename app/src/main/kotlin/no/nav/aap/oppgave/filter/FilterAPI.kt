package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metrikker.httpCallCounter
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentFilterApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) {

    route("/filter").get<FilterRequestDto, List<FilterResponse>> { req ->
        prometheus.httpCallCounter("/filter").increment()
        val filterListe = dataSource.transaction(readOnly = true) { connection ->
            FilterRepository(connection).hentForEnheter(req.enheter)
        }.map { it.tilResponse() }
        respond(filterListe)
    }
    route("/filter/v2").get<FilterRequestDto, List<FilterResponse>> { req ->
        prometheus.httpCallCounter("/filter/v2").increment()
        val filterListe = dataSource.transaction(readOnly = true) { connection ->
            FilterRepository(connection).hentForEnheter(req.enheter)
        }.map { it.tilResponse() }
        respond(filterListe)
    }
}
