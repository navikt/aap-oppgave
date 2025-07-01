package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.mottattdokument.MottattDokumentService
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import java.util.*
import javax.sql.DataSource

fun NormalOpenAPIRoute.avreserverOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/avreserver-oppgave").post<Unit, List<OppgaveId>, AvklaringsbehovReferanseDto> { _, dto ->
        prometheus.httpCallCounter("avreserver-oppgave").increment()
        val oppgaver = dataSource.transaction { connection ->
            val oppgaverSomSkalAvreserveres = OppgaveRepository(connection).hentÅpneOppgaver(dto)
            val reserverOppgaveService = ReserverOppgaveService(
                OppgaveRepository(connection),
                FlytJobbRepository(connection)
            )
            val ident = ident()
            oppgaverSomSkalAvreserveres.forEach {
                reserverOppgaveService.avreserverOppgave(it, ident)
            }
            oppgaverSomSkalAvreserveres
        }
        respond(oppgaver)
    }


fun NormalOpenAPIRoute.flyttOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/flytt-oppgave").post<Unit, List<OppgaveId>, FlyttOppgaveDto> { _, dto ->
        prometheus.httpCallCounter("flytt-oppgave").increment()

        val oppgaver = dataSource.transaction { connection ->
            val innloggetBrukerIdent = ident()
            val token = token()
            val reserverOppgaveService = ReserverOppgaveService(
                OppgaveRepository(connection),
                FlytJobbRepository(connection)
            )
            reserverOppgaveService.reserverOppgave(dto.avklaringsbehovReferanse, innloggetBrukerIdent, token)
        }
        respond(oppgaver)

    }

fun NormalOpenAPIRoute.kvitterLegeerklæring(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/kvitter-legeerklaring").post<Unit, Unit, KvitterLegeerklæringDto> { _, dto ->
        prometheus.httpCallCounter("kvitter-legeerklaring").increment()

        dataSource.transaction { connection ->
            val mottattDokumentService = MottattDokumentService(
                MottattDokumentRepository(connection),
                OppgaveRepository(connection),
            )

            mottattDokumentService.kvitterLegeerklæring(
                behandlingRef = dto.behandlingRef,
                ident = ident()
            )
        }

        respondWithStatus(HttpStatusCode.OK)
    }

data class KvitterLegeerklæringDto(
    val behandlingRef: UUID
)