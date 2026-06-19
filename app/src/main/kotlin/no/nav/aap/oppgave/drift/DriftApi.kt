package no.nav.aap.oppgave.drift

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import java.time.LocalDateTime
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.EnhetFilter
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.filter.Filtermodus
import no.nav.aap.oppgave.filter.MarkeringFilter
import no.nav.aap.oppgave.filter.OppdaterFilter
import no.nav.aap.oppgave.filter.OpprettFilter
import no.nav.aap.oppgave.historikk.OppgaveHistorikk
import no.nav.aap.oppgave.historikk.OppgaveHistorikkRepository
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.tilbakekreving.TilbakeKrevingAvklaringsbehovKoder
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
                    val historikkRepository = OppgaveHistorikkRepository(connection)

                    OppgavelisteService(
                        OppgaveRepository(connection),
                        MarkeringRepository(connection),
                        enhetService,
                        norgGateway
                    )
                        .hentOppgaverForBehandling(params.referanse)
                        .map { it.mapTilOppgaveDriftsinfo(historikkRepository.hentHistorikkForOppgave(it.id!!)) }
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
                    val markeringFilter = request.markeringer.map { MarkeringFilter(it.type, it.filtermodus) }

                    if (request.id != null) {
                        filterRepo.oppdater(
                            OppdaterFilter(
                                id = request.id,
                                navn = request.navn,
                                beskrivelse = request.beskrivelse,
                                avklaringsbehovtyper = request.avklaringsbehovKoder,
                                behandlingstyper = request.behandlingstyper,
                                enhetFilter = enhetFilter,
                                markeringer = markeringFilter,
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
                                markeringer = markeringFilter,
                                type = request.type,
                                opprettetAv = ident(),
                                opprettetTidspunkt = LocalDateTime.now(),
                            )
                        )
                    }
                }

                val lagretFilter = dataSource.transaction(readOnly = true) { connection ->
                    val filterRepo = FilterRepository(connection)
                    val filter =
                        requireNotNull(filterRepo.hent(filterId)) { "Filter $filterId ikke funnet etter lagring" }
                    val enheter = filterRepo.hentAlleFilterEnheter()
                    filter.tilDriftResponse(enheter)
                }
                respond(lagretFilter)
            }
        }

        route("/filter/slett") {
            authorizedPost<Unit, Unit, SlettFilterRequest>(
                RollerConfig(listOf(Drift))
            ) { _, request ->
                val filter = dataSource.transaction(readOnly = true) { connection ->
                    FilterRepository(connection).hent(request.id)
                }

                if (filter == null) {
                    respondWithStatus(HttpStatusCode.NotFound)
                    return@authorizedPost
                }

                if (filter.opprettetAv != ident()) {
                    respondWithStatus(HttpStatusCode.Forbidden)
                    return@authorizedPost
                }

                dataSource.transaction { connection ->
                    FilterRepository(connection).slettFilter(request.id)
                }
                respondWithStatus(HttpStatusCode.NoContent)
            }
        }
    }
}

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
    inkluderteMarkeringer = inkluderteMarkeringer.toList(),
    ekskluderteMarkeringer = ekskluderteMarkeringer.toList(),
    opprettetAv = opprettetAv,
    opprettetTidspunkt = opprettetTidspunkt,
    endretAv = endretAv,
    endretTidspunkt = endretTidspunkt,
)

private fun OppgaveDto.mapTilOppgaveDriftsinfo(historikk: List<OppgaveHistorikk>) = OppgaveDriftsinfoDTO(
    oppgaveId = id!!,
    behandlingRef = behandlingRef,
    status = status,
    enhet = enhet,
    oppfølgingsenhet = oppfølgingsenhet,
    reservertAv = reservertAv,
    veilederArbeid = veilederArbeid,
    veilederSykdom = veilederSykdom,
    opprettetTidspunkt = opprettetTidspunkt,
    endretTidspunkt = endretTidspunkt,
    avklaringsbehovKode = avklaringsbehovKode,
    historikk = historikk.map {
        OppgaveHistorikkDto(
            status = it.status,
            reservertAv = it.reservertAv,
            reservertTidspunkt = it.reservertTidspunkt,
            endretAv = it.endretAv,
            endretTidspunkt = it.endretTidspunkt,
            enhet = it.enhet,
            oppfølgingsenhet = it.oppfølgingsenhet,
        )
    }
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
