package no.nav.aap.oppgave.avklaringsbehov

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.metriker.httpCallCounter
import no.nav.aap.oppgave.opprett.AvklaringsbehovRequest
import no.nav.aap.oppgave.server.authenticate.ident
import java.time.LocalDateTime
import javax.sql.DataSource

fun NormalOpenAPIRoute.avklaringsbehovApi(dataSource: DataSource,prometheus: PrometheusMeterRegistry) =

    route("/avklaringsbehov").post<Unit, OppgaveId, AvklaringsbehovRequest> { _, request ->
        prometheus.httpCallCounter("/api/avklaringsbehov").increment()
        val oppgaveId =  dataSource.transaction(readOnly = true) { connection ->
            val ident = ident()

            val oppgave = Oppgave(
                saksnummer = request.saksnummer,
                behandlingRef = request.behandlingRef,
                behandlingOpprettet = request.behandlingOpprettet,
                avklaringsbehovKode = request.avklaringsbehovKode,
                opprettetAv = ident,
                opprettetTidspunkt = LocalDateTime.now()
            )
            OppgaveRepository(connection).opprettOppgave(oppgave)
        }
        respond(oppgaveId)
    }



