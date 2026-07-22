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
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.SaksnummerPathParam
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import javax.sql.DataSource

/**
 * Henter nyeste oppgave med status "OPPRETTET" gitt en behandlingsreferanse.
 */
fun NormalOpenAPIRoute.hentOppgaveApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
    prometheus: PrometheusMeterRegistry
) {
    route("/{referanse}/hent-oppgave").get<BehandlingReferanse, OppgaveDto> { request ->
        prometheus.httpCallCounter("/hent-oppgave").increment()
        val oppgave = dataSource.transaction(readOnly = true) { connection ->
            OppgavelisteService(
                OppgaveRepository(connection),
                MarkeringRepository(connection),
                enhetService,
                norgGateway
            ).hentAktivOppgave(request)
        }

        if (oppgave != null) {
            respond(oppgave.hentPersonNavn().tilOppgaveDto())
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }

    route("/{saksnummer}/hent-oppgaver-paa-sak").get<SaksnummerPathParam, OppgaverPåSakResponse> { request ->
        prometheus.httpCallCounter("/hent-oppgaver-paa-sak").increment()
        val oppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentAktiveOppgaverPåSak(request.saksnummer)
        }

        respond(
            OppgaverPåSakResponse(
                oppgaver = oppgaver.map { it.tilOppgavePåSakResponse() }
            )
        )
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
                enhetService,
                norgGateway
            ).hentAktivOppgave(request)
        }

        if (oppgave != null) {
            respond(oppgave.tilOppgaveVisningsinformasjonResponse())
        } else {
            respondWithStatus(HttpStatusCode.NoContent)
        }
    }

private fun Oppgave.hentPersonNavn() = listOf(this).hentPersonNavn().first()

private fun Oppgave.tilOppgaveVisningsinformasjonResponse() = OppgaveVisningsinformasjonResponse(
    id = requireNotNull(id) { "Oppgave må ha ID" },
    versjon = versjon,
    saksnummer = saksnummer,
    reservertAvNavn = reservertAvNavn,
    reservertAvIdent = reservertAv,
    returInformasjon = returInformasjon?.tilReturInformasjonDto(),
    markeringer = markeringer,
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
        harStrengtFortroligAdresse = enhet == Enhet.NAV_VIKAFOSSEN.kode,
        harFortroligAdresse = harFortroligAdresse == true,
        erSkjermet = erSkjermet == true
    ),
    harUlesteDokumenter = harUlesteDokumenter == true
)

private fun Oppgave.tilOppgavePåSakResponse(): OppgavePåSakResponse {
    return OppgavePåSakResponse(
        id = requireNotNull(id) { "Oppgave må ha ID" },
        versjon = versjon,
        behandlingsreferanse = behandlingRef,
        reservertAvIdent = reservertAv,
        reservertAvNavn = reservertAvNavn,
    )
}

