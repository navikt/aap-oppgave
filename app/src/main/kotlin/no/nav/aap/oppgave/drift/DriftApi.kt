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
import no.nav.aap.oppgave.filter.EnhetFilter
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.filter.FilterType
import no.nav.aap.oppgave.filter.Filtermodus
import no.nav.aap.oppgave.filter.OpprettFilter
import no.nav.aap.oppgave.filter.OppdaterFilter
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import no.nav.aap.tilgang.Drift
import no.nav.aap.tilgang.RollerConfig
import no.nav.aap.tilgang.authorizedGet
import no.nav.aap.tilgang.authorizedPost

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

                    val filtre = alleFilter.map { filter -> filter.tilDriftResponse(enhetPerFilter) }

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

            authorizedPost<Unit, FilterDriftResponse, FilterDriftRequest>(
                RollerConfig(listOf(Drift))
            ) { _, request ->
                val filterId = dataSource.transaction { connection ->
                    val filterRepo = FilterRepository(connection)
                    val enhetFilter = request.enheter.map { EnhetFilter(it.enhet, it.filtermodus) }

                    if (request.id != null) {
                        filterRepo.oppdater(
                            OppdaterFilter(
                                id = request.id,
                                navn = request.navn,
                                beskrivelse = request.beskrivelse,
                                avklaringsbehovtyper = request.avklaringsbehovKoder,
                                behandlingstyper = request.behandlingstyper,
                                enhetFilter = enhetFilter,
                                endretAv = ident(),
                                endretTidspunkt = LocalDateTime.now(),
                            )
                        )
                    } else {
                        filterRepo.opprett(
                            OpprettFilter(
                                navn = request.navn,
                                beskrivelse = request.beskrivelse,
                                avklaringsbehovtyper = request.avklaringsbehovKoder,
                                behandlingstyper = request.behandlingstyper,
                                enhetFilter = enhetFilter,
                                type = request.type,
                                opprettetAv = ident(),
                                opprettetTidspunkt = LocalDateTime.now(),
                            )
                        )
                    }
                }

                val lagretFilter = dataSource.transaction(readOnly = true) { connection ->
                    val filterRepo = FilterRepository(connection)
                    val filter = requireNotNull(filterRepo.hent(filterId)) { "Filter $filterId ikke funnet etter lagring" }
                    val enheter = filterRepo.hentAlleFilterEnheter()
                    filter.tilDriftResponse(enheter)
                }
                respond(lagretFilter)
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
    val filtre: List<FilterDriftResponse>,
    val avklaringsbehovUtenFilter: List<AvklaringsbehovDto>,
)

private data class FilterDriftResponse(
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

data class FilterDriftRequest(
    val id: Long? = null,
    val navn: String,
    val beskrivelse: String,
    val type: FilterType = FilterType.GENERELL,
    val avklaringsbehovKoder: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val enheter: List<EnhetDriftRequest> = emptyList(),
)

data class EnhetDriftRequest(
    val enhet: String,
    val filtermodus: Filtermodus,
)

private data class AvklaringsbehovDto(val kode: String, val navn: String)

private fun FilterDto.tilDriftResponse(enhetPerFilter: Map<Long, List<EnhetFilter>>) = FilterDriftResponse(
    id = id!!,
    navn = navn,
    beskrivelse = beskrivelse,
    type = type,
    avklaringsbehov = avklaringsbehovKoder.map { AvklaringsbehovDto(it, utledAvklaringsbehovnavn(it)) }.toSet(),
    behandlingstyper = behandlingstyper,
    inkluderteEnheter = (enhetPerFilter[id] ?: emptyList())
        .filter { it.filtermodus == Filtermodus.INKLUDER }
        .map { it.enhetNr },
    ekskluderteEnheter = (enhetPerFilter[id] ?: emptyList())
        .filter { it.filtermodus == Filtermodus.EKSKLUDER }
        .map { it.enhetNr },
    opprettetAv = opprettetAv,
    opprettetTidspunkt = opprettetTidspunkt,
    endretAv = endretAv,
    endretTidspunkt = endretTidspunkt,
)

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
        .recoverCatching { TilbakeKrevingAvklaringsbehovKoder.fraKode(kode).name }
        .getOrDefault(kode)
