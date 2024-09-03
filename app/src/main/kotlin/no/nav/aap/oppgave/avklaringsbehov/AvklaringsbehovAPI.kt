package no.nav.aap.oppgave.avklaringsbehov

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.komponenter.dbconnect.transaction
import javax.sql.DataSource

fun NormalOpenAPIRoute.avklaringsbehovApi(dataSource: DataSource) {
    route("/api/avklaringsbehov") {
        route("/").post<Unit, List<OppgaveId>, AvklaringsbehovRequest> { _, dto ->

            dataSource.transaction(readOnly = true) {


            }

            respond(listOf())
        }
    }

}