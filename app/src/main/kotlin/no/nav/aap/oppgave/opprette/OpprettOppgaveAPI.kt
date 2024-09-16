package no.nav.aap.oppgave.opprette

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.verdityper.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.metriker.httpCallCounter
import no.nav.aap.oppgave.opprett.OpprettOppgaveDto
import no.nav.aap.oppgave.server.authenticate.ident
import java.time.LocalDateTime
import javax.sql.DataSource

fun NormalOpenAPIRoute.opprettOppgaveApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/opprett-oppgave").post<Unit, OppgaveId, OpprettOppgaveDto> { _, request ->
        prometheus.httpCallCounter("/opprett-oppgave").increment()
        val oppgaveId =  dataSource.transaction { connection ->
            val oppgaveDto = OppgaveDto(
                saksnummer = request.saksnummer,
                behandlingRef = request.behandlingRef,
                behandlingOpprettet = request.behandlingOpprettet,
                avklaringsbehovType = request.avklaringsbehovType,
                opprettetAv = ident(),
                opprettetTidspunkt = LocalDateTime.now()
            )
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
        respond(oppgaveId)
    }



