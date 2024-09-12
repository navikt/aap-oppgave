package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metriker.httpCallCounter
import javax.sql.DataSource

fun NormalOpenAPIRoute.filterApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/filter").get<Unit, List<FilterDto>> { _ ->
        prometheus.httpCallCounter("/filter").increment()
        val filterListe = dataSource.transaction(readOnly = true) { connection ->
            FilterRepository(connection).hentAlleFilter()
        }
        respond(filterListe)
    }