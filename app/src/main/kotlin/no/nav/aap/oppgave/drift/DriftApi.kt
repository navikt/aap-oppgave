package no.nav.aap.oppgave.drift

import no.nav.aap.oppgave.tilbakekreving.TilbakeKrevingAvklaringsbehovKoder
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import java.time.LocalDateTime
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.EnhetFilter
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.filter.Filtermodus
import no.nav.aap.oppgave.filter.OpprettFilter
import no.nav.aap.oppgave.filter.OppdaterFilter
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.server.authenticate.ident
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
