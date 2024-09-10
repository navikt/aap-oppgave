package no.nav.aap.oppgave.avklaringsbehov

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.komponenter.dbconnect.transaction
import java.time.LocalDateTime
import javax.sql.DataSource

fun NormalOpenAPIRoute.avklaringsbehovApi(dataSource: DataSource) {
    route("/api") {
        route("/avklaringsbehov").post<Unit, OppgaveId, AvklaringsbehovRequest> { _, dto ->
            val oppgaveId =  dataSource.transaction(readOnly = true) { connection ->

                val ident = "ukjent" //TODO fiks dette

                val oppgave = Oppgave(
                    saksnummer = dto.saksnummer,
                    behandlingRef = dto.behandlingRef,
                    behandlingOpprettet = dto.behandlingOpprettet,
                    avklaringsbehovKode = dto.avklaringsbehovKode,
                    opprettetAv = ident,
                    opprettetTidspunkt = LocalDateTime.now()
                )
                OppgaveRepository(connection).opprettOppgave(oppgave)
            }
            respond(oppgaveId)
        }
    }


}