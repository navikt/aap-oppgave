package no.nav.aap.oppgave.drift

import no.nav.aap.oppgave.tilbakekreving.TilbakeKrevingAvklaringsbehovKoder
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.filter.FilterType
import no.nav.aap.oppgave.filter.Filtermodus
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import no.nav.aap.tilgang.Drift
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.authorizedGet

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

        route("/filter") {
            authorizedGet<Unit, DriftFilterResponsDTO>(
                RollerConfig(listOf(Drift))
            ) { _ ->
                val respons = dataSource.transaction(readOnly = true) { connection ->
                    val filterRepo = FilterRepository(connection)
                    val alleFilter = filterRepo.hentAlle()
                    val enhetPerFilter = filterRepo.hentAlleFilterEnheter()

                    val filtre = alleFilter.map { filter ->
                        FilterDriftsinfoDTO(
                            id = filter.id!!,
                            navn = filter.navn,
                            beskrivelse = filter.beskrivelse,
                            type = filter.type,
                            avklaringsbehov = filter.avklaringsbehovKoder.map {
                                AvklaringsbehovDto(it, utledAvklaringsbehovnavn(it))
                            }.toSet(),
                            behandlingstyper = filter.behandlingstyper,
                            inkluderteEnheter = (enhetPerFilter[filter.id] ?: emptyList())
                                .filter { it.filtermodus == Filtermodus.INKLUDER }
                                .map { it.enhetNr },
                            ekskluderteEnheter = (enhetPerFilter[filter.id] ?: emptyList())
                                .filter { it.filtermodus == Filtermodus.EKSKLUDER }
                                .map { it.enhetNr },
                            opprettetAv = filter.opprettetAv,
                            opprettetTidspunkt = filter.opprettetTidspunkt,
                            endretAv = filter.endretAv,
                            endretTidspunkt = filter.endretTidspunkt,
                        )
                    }

                    val koderIFiltre = alleFilter.flatMap { it.avklaringsbehovKoder }.toSet()
                    val udekkede = (hentAlleManuelleAvklaringsbehovKoder() - koderIFiltre)
                        .map { AvklaringsbehovDto(it, utledAvklaringsbehovnavn(it)) }

                    DriftFilterResponsDTO(
                        filtre = filtre,
                        avklaringsbehovUtenFilter = udekkede,
                    )
                }
                respond(respons)
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

private data class DriftFilterResponsDTO(
    val filtre: List<FilterDriftsinfoDTO>,
    val avklaringsbehovUtenFilter: List<AvklaringsbehovDto>,
)

private data class FilterDriftsinfoDTO(
    val id: Long,
    val navn: String,
    val beskrivelse: String,
    val type: FilterType,
    val avklaringsbehov: Set<AvklaringsbehovDto>,
    val behandlingstyper: Set<Behandlingstype>,
    val inkluderteEnheter: List<String>,
    val ekskluderteEnheter: List<String>,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String?,
    val endretTidspunkt: LocalDateTime?,
)

private data class AvklaringsbehovDto(val kode: String, val navn: String)

private fun hentAlleManuelleAvklaringsbehovKoder(): Set<String> {
    val manuelleBehovTyper = setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG, BehovType.OVERSTYR)
    val behandlingsflytKoder = Definisjon.entries
        .filter { it.type in manuelleBehovTyper }
        .map { it.kode.name }
        .toSet()

    val postmottakManuelleTyper = setOf(
        PostmottakDefinisjon.BehovType.MANUELT_PÅKREVD,
        PostmottakDefinisjon.BehovType.MANUELT_FRIVILLIG,
    )
    val postmottakKoder = PostmottakDefinisjon.entries
        .filter { it.type in postmottakManuelleTyper }
        .map { it.kode.name }
        .toSet()

    val tilbakekrevingKoder = TilbakeKrevingAvklaringsbehovKoder.entries
        .map { it.kode }
        .toSet()

    return behandlingsflytKoder + postmottakKoder + tilbakekrevingKoder
}

private fun utledAvklaringsbehovnavn(kode: String): String =
    runCatching { Definisjon.forKode(kode).name }
        .recoverCatching { PostmottakDefinisjon.forKode(kode).name }
        .recoverCatching { TilbakeKrevingAvklaringsbehovKoder.valueOf(kode).name }
        .getOrDefault(kode)
