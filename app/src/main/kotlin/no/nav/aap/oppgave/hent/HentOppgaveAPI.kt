package no.nav.aap.oppgave.hent

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.SaksnummerPathParam
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.markering.tilDto
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.uføreVedtak.UføreVedtakRepository
import no.nav.aap.oppgave.uføreVedtak.tilUføreVedtakRespsons
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentOppgaveApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry
) {
    route("/{saksnummer}/hent-oppgaver-paa-sak").get<SaksnummerPathParam, OppgaverPåSakResponse> { request ->
        prometheus.httpCallCounter("/hent-oppgaver-paa-sak").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentAktiveOppgaverPåSak(request.saksnummer)
        }

        respond(
            OppgaverPåSakResponse(
                oppgaver = oppgaver.map { it.tilOppgavePåBehandlingResponse() }
            )
        )
    }

    route("/{referanse}/hent-saksnummer").get<BehandlingReferanse, SaksnummerResponse> { request ->
        prometheus.httpCallCounter("/hent-saksnummer").increment()
        val oppgave = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentAktivOppgave(request)
        }

        if (oppgave?.saksnummer != null) {
            respond(
                SaksnummerResponse(
                    saksnummer = oppgave.saksnummer
                )
            )
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }
}

fun NormalOpenAPIRoute.hentOppgaveVisningsinformasjonApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
    prometheus: PrometheusMeterRegistry
) =
    route("/{referanse}/hent-oppgave-visningsinformasjon").get<BehandlingReferanse, OppgaveVisningsinformasjonResponse> { request ->
        prometheus.httpCallCounter("/hent-oppgave-visningsinformasjon").increment()
        val oppgave = dataSource.transaction(readOnly = true) { connection ->
            OppgavelisteService(
                OppgaveRepository(connection),
                MarkeringRepository(connection),
                UføreVedtakRepository(connection),
                enhetService,
            ).hentAktivOppgave(request)
        }

        if (oppgave != null) {
            respond(oppgave.tilOppgaveVisningsinformasjonResponse())
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }

private fun Oppgave.tilOppgaveVisningsinformasjonResponse() = OppgaveVisningsinformasjonResponse(
    id = requireNotNull(id) { "Oppgave må ha ID" },
    versjon = versjon,
    saksnummer = saksnummer,
    reservertAvNavn = reservertAvNavn,
    reservertAvIdent = reservertAv,
    returInformasjon = returInformasjon?.tilReturInformasjonDto(),
    markeringer = markeringer.tilDto(),
    uførevedtakinfo = uføreVedtak?.tilUføreVedtakRespsons(),
    påVentInfo = påVentTil?.let {
        VenteInformasjonResponse(
            påVentTil = it,
            påVentÅrsak = requireNotNull(påVentÅrsak) { "Venteårsak kan ikke være null dersom ventefrist er satt" },
            venteBegrunnelse = venteBegrunnelse
        )
    },
    utløptVenteInfo = utløptVentefrist?.let {
        VenteInformasjonResponse(
            påVentTil = it,
            påVentÅrsak = forrigePåVentÅrsak,
            venteBegrunnelse = forrigeVenteBegrunnelse
        )
    },
    skjermingInfo = SkjermingInfoResponse(
        harStrengtFortroligAdresse = harStrengtFortroligAdresse,
        harFortroligAdresse = harFortroligAdresse == true,
        erSkjermet = erSkjermet == true
    ),
    harUlesteDokumenter = harUlesteDokumenter == true
)

private fun Oppgave.tilOppgavePåBehandlingResponse(): OppgavePåBehandlingResponse {
    return OppgavePåBehandlingResponse(
        id = requireNotNull(id) { "Oppgave må ha ID" },
        versjon = versjon,
        behandlingsreferanse = behandlingRef,
        reservertAvIdent = reservertAv,
        reservertAvNavn = reservertAvNavn,
    )
}

