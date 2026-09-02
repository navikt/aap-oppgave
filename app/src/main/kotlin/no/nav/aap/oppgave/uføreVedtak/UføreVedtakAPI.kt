package no.nav.aap.oppgave.uføreVedtak

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource

fun NormalOpenAPIRoute.uføreVedtakApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry
) {
    route("/fjern-uførevedtak-ikon").post<Unit, Unit, UføreVedtak> { _, dto ->
        prometheus.httpCallCounter("/fjern-uførevedtak-ikon").increment()

        dataSource.transaction { connection ->
            val uføreVedtakService = UføreVedtakService(
                UføreVedtakRepository(connection)
            )

            uføreVedtakService.fjernUføreVedtakPåBehandling(
                behandlingRef = dto.referanse,
                ident = ident()
            )
        }

        respondWithStatus(HttpStatusCode.OK)
    }
}

