package no.nav.aap.oppgave.mottattdokument

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.DokumenterLestDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource

fun NormalOpenAPIRoute.mottattDokumentApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/mottatt-dokumenter-lest").post<Unit, Unit, DokumenterLestDto> { _, dto ->
        prometheus.httpCallCounter("/mottatt-dokumenter-lest").increment()

        dataSource.transaction { connection ->
            val mottattDokumentService = MottattDokumentService(
                MottattDokumentRepository(connection),
                OppgaveRepository(connection),
            )

            mottattDokumentService.registrerDokumenterLest(
                behandlingRef = dto.behandlingRef,
                ident = ident()
            )
        }

        respondWithStatus(HttpStatusCode.OK)
    }