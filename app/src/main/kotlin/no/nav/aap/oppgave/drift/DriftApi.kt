package no.nav.aap.oppgave.drift

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.verdityper.Status

fun NormalOpenAPIRoute.driftApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
) {
    route("/api/drift") {
        route("/oppgave/behandling/{referanse}") {
            post<BehandlingReferanse, List<OppgaveDriftsinfoDTO>, Unit> { params, _ ->
                val oppgaver = dataSource.transaction { connection ->
                    OppgavelisteService(
                        OppgaveRepository(connection),
                        MarkeringRepository(connection),
                        enhetService,
                        norgGateway
                    )
                        .hentOppgaverForBehandling(params.referanse)
                        .map {
                            OppgaveDriftsinfoDTO(
                                oppgaveId = it.id!!,
                                behandlingRef = it.behandlingRef,
                                status = it.status,
                                enhet = it.enhet,
                                oppfølgingsenhet = it.oppfølgingsenhet,
                                reservertAv = it.reservertAv,
                                veilederArbeid = it.veilederArbeid,
                                veilederSykdom = it.veilederSykdom,
                                opprettetTidspunkt = it.opprettetTidspunkt,
                                endretTidspunkt = it.endretTidspunkt,
                                avklaringsbehovKode = it.avklaringsbehovKode,
                            )
                        }
                        .sortedByDescending { it.opprettetTidspunkt }
                }

                respond(oppgaver)
            }

        }
    }
}

private data class OppgaveDriftsinfoDTO(
    val oppgaveId: Long,
    val behandlingRef: UUID,
    val status: Status,
    val enhet: String,
    val oppfølgingsenhet: String?,
    val reservertAv: String?,
    val veilederArbeid: String?,
    val veilederSykdom: String?,
    val opprettetTidspunkt: LocalDateTime,
    val endretTidspunkt: LocalDateTime?,
    val avklaringsbehovKode: String
)
