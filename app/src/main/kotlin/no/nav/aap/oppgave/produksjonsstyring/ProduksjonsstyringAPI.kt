package no.nav.aap.oppgave.produksjonsstyring

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metrikker.httpCallCounter
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentAntallOppgaver(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/produksjonsstyring/antall-oppgaver").post<Unit, Map<String, Int>, AntallOppgaverDto> { _, req ->
        prometheus.httpCallCounter("/produksjonsstyring/antall-oppgaver").increment()
        val åpneOppgaverForBehandlingstype = dataSource.transaction(readOnly = true) { connection ->
            ProduksjonsstyringRepository(connection)
                .hentAntallÅpneOppgaver(req.behandlingstype)
                .map {it.key.kode to it.value}.toMap()
        }
        respond(åpneOppgaverForBehandlingstype)
    }