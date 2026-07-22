package no.nav.aap.oppgave.oppgaveliste

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.ForrigeKvalitetssikrerInfo
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.ReturInformasjonDto
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.hent.SkjermingInfoResponse
import no.nav.aap.oppgave.hent.VenteInformasjonResponse
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.liste.ListeOppgaveResponse
import no.nav.aap.oppgave.liste.OppgaveMetadataResponse
import no.nav.aap.oppgave.liste.OppgavelisteRequest
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.liste.OppgavelisteResponsV2
import no.nav.aap.oppgave.liste.OppgavelisteTagsResponse
import no.nav.aap.oppgave.liste.PersonOgEnhetResponse
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import no.nav.aap.oppgave.server.authenticate.ident
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("oppgavelisteApi")

/**
 * Søker etter oppgaver med et predefinert filter angitt med filterId. Det vil bli sjekket om innlogget bruker har tilgang
 * til oppgavene. I tillegg kan det legges på en begrensning på enheter.
 */
fun NormalOpenAPIRoute.oppgavelisteApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
    prometheus: PrometheusMeterRegistry,
) {
    route("/oppgaveliste").post<Unit, OppgavelisteRespons, OppgavelisteRequest> { _, request ->
        prometheus.httpCallCounter("/oppgaveliste").increment()
        val (data, bruktBehanlingstyperIFilter) =
            dataSource.transaction(readOnly = true) { connection ->
                log.info("Henter filter med filterId ${request.filterId}")
                val filter =
                    requireNotNull(FilterRepository(connection).hent(request.filterId)) { "filter kan ikke være null. filterId: ${request.filterId}" }
                val veilederIdent =
                    if (request.veileder) {
                        ident()
                    } else {
                        null
                    }
                Pair(
                    OppgavelisteService(
                        oppgaveRepository = OppgaveRepository(connection),
                        markeringRepository = MarkeringRepository(connection),
                        norgGateway = norgGateway,
                        enhetService = enhetService,
                    ).hentOppgaverMedTilgang(
                        request.utvidetFilter,
                        request.enheter,
                        request.paging,
                        request.kunLedigeOppgaver == true,
                        filter,
                        veilederIdent,
                        token(),
                        ident(),
                        request.sortering?.sortBy,
                        request.sortering?.sortOrder,
                        request.hastemarkeringerFørst == true
                    ), filter.behandlingstyper
                )
            }

        respond(
            OppgavelisteRespons(
                antallTotalt = data.antallTotalt,
                oppgaver = data.oppgaver.hentPersonNavn().map { it.tilOppgaveDto() },
                antallGjenstaaende = data.antallGjenstaaende,
                sattFilterBehandlingstyper = bruktBehanlingstyperIFilter
            )
        )
    }

    route("/oppgaveliste/v2").post<Unit, OppgavelisteResponsV2, OppgavelisteRequest> { _, request ->
        prometheus.httpCallCounter("/oppgaveliste/v2").increment()
        val (data, bruktBehanlingstyperIFilter) =
            dataSource.transaction(readOnly = true) { connection ->
                log.info("Henter filter med filterId ${request.filterId}")
                val filter =
                    requireNotNull(FilterRepository(connection).hent(request.filterId)) { "filter kan ikke være null. filterId: ${request.filterId}" }
                val veilederIdent =
                    if (request.veileder) {
                        ident()
                    } else {
                        null
                    }
                Pair(
                    OppgavelisteService(
                        oppgaveRepository = OppgaveRepository(connection),
                        markeringRepository = MarkeringRepository(connection),
                        norgGateway = norgGateway,
                        enhetService = enhetService,
                    ).hentOppgaverMedTilgang(
                        request.utvidetFilter,
                        request.enheter,
                        request.paging,
                        request.kunLedigeOppgaver == true,
                        filter,
                        veilederIdent,
                        token(),
                        ident(),
                        request.sortering?.sortBy,
                        request.sortering?.sortOrder,
                        request.hastemarkeringerFørst == true
                    ), filter.behandlingstyper
                )
            }

        respond(
            OppgavelisteResponsV2(
                antallTotalt = data.antallTotalt,
                oppgaver = data.oppgaver.hentPersonNavn().map { it.tilListeOppgaveResponse() },
                antallGjenstaaende = data.antallGjenstaaende,
                sattFilterBehandlingstyper = bruktBehanlingstyperIFilter
            )
        )
    }
}

private fun Oppgave.tilListeOppgaveResponse(): ListeOppgaveResponse {
    return ListeOppgaveResponse(
        behandlingOpprettet = behandlingOpprettet,
        avklaringsbehovKode = avklaringsbehovKode,
        vurderingsbehov = vurderingsbehov,
        årsakTilOpprettelse = årsakTilOpprettelse,
        oppgaveMetadataResponse = OppgaveMetadataResponse(
            id = requireNotNull(id) { "Oppgave må ha ID" },
            versjon = versjon,
            status = status,
            opprettetTidspunkt = opprettetTidspunkt
        ),
        behandlingskontekstResponse = BehandlingskontekstResponse(
            behandlingsreferanse = behandlingRef,
            journalpostId = journalpostId,
            saksnummer = saksnummer,
            behandlingstype = behandlingstype,
            tilbakekrevingUrl = tilbakekrevingsVars?.tilbakekrevings_URL
        ),
        personOgEnhetResponse = PersonOgEnhetResponse(
            personIdent = requireNotNull(personIdent) { "Oppgave må ha personIdent" },
            personNavn = personNavn,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            enhetForrigeOppgave = enhetForrigeOppgave
        ),
        oppgavelisteTagsResponse = OppgavelisteTagsResponse(
            påVentInfo = påVentTil?.let {
                VenteInformasjonResponse(
                    påVentTil = påVentTil,
                    påVentÅrsak = requireNotNull(påVentÅrsak) { "Oppgave har ventefrist men ikke årsak" },
                    venteBegrunnelse = venteBegrunnelse
                )
            },
            forrigePåVentInfo = utløptVentefrist?.let {
                VenteInformasjonResponse(
                    påVentTil = utløptVentefrist,
                    påVentÅrsak = requireNotNull(forrigePåVentÅrsak) { "Oppgave har utløpt ventefrist men ikke årsak" },
                    venteBegrunnelse = forrigeVenteBegrunnelse
                )
            },
            returInformasjon = returInformasjon?.let {
                ReturInformasjonDto(
                    status = it.status,
                    årsaker = it.årsaker,
                    begrunnelse = it.begrunnelse,
                    endretAv = it.endretAv
                )
            },
            skjermingInfoResponse = SkjermingInfoResponse(
                harStrengtFortroligAdresse = harStrengtFortroligAdresse,
                harFortroligAdresse = harFortroligAdresse == true,
                erSkjermet = erSkjermet == true
            ),
            harUlesteDokumenter = harUlesteDokumenter == true,
            markeringer = markeringer,
            forrigeKvalitetssikrerInfo = forrigeKvalitetssikrerInfo?.let {
                ForrigeKvalitetssikrerInfo(
                    forrigeKvalitetssikrerIdent = it.forrigeKvalitetssikrerIdent,
                    forrigeKvalitetssikrerNavn = it.forrigeKvalitetssikrerNavn
                )
            }
        ),
        veilederArbeid = veilederArbeid,
        veilederSykdom = veilederSykdom,
        reservertAv = reservertAv,
        reservertAvNavn = reservertAvNavn,
        tilbakekrevingsVarsDto = tilbakekrevingsVars?.tilDto()
    )
}
