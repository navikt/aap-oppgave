package no.nav.aap.oppgave.oppdater

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import javax.sql.DataSource
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.oppdater.hendelse.tilOppgaveOppdatering
import no.nav.aap.oppgave.tilbakekreving.TilbakekrevingRepository
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.tilgang.AuthorizationBodyPathConfig
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.authorizedPost
import org.slf4j.LoggerFactory
import org.slf4j.MDC

fun NormalOpenAPIRoute.oppdaterBehandlingOppgaverApi(
    dataSource: DataSource,
    msGraphClient: IMsGraphGateway,
    prometheus: PrometheusMeterRegistry
) = route("/oppdater-oppgaver").authorizedPost<Unit, Unit, BehandlingFlytStoppetHendelse>(
    routeConfig = AuthorizationBodyPathConfig(
        operasjon = Operasjon.SAKSBEHANDLE,
        applicationsOnly = true,
        applicationRole = "oppdater-behandlingsflyt-oppgaver",
    )
) { _, request ->
    prometheus.httpCallCounter("/oppdater-oppgaver").increment()
    MDC.putCloseable("saksnummer", request.saksnummer.toString()).use {
        dataSource.transaction { connection ->
            OppdaterOppgaveService(
                msGraphClient,
                oppgaveRepository = OppgaveRepository(connection),
                flytJobbRepository = FlytJobbRepositoryImpl(connection),
                mottattDokumentRepository = MottattDokumentRepository(connection),
                tilbakekrevingRepository = TilbakekrevingRepository(connection),
            ).håndterNyOppgaveOppdatering(
                request.tilOppgaveOppdatering()
            )
        }
    }
    respondWithStatus(HttpStatusCode.OK)
}

fun NormalOpenAPIRoute.oppdaterPostmottakOppgaverApi(
    dataSource: DataSource,
    msGraphClient: IMsGraphGateway,
    prometheus: PrometheusMeterRegistry
) = route("/oppdater-postmottak-oppgaver").authorizedPost<Unit, Unit, DokumentflytStoppetHendelse>(
    routeConfig = AuthorizationBodyPathConfig(
        operasjon = Operasjon.SAKSBEHANDLE,
        applicationsOnly = true,
        applicationRole = "oppdater-postmottak-oppgaver"
    )
) { _, request ->
    prometheus.httpCallCounter("/oppdater-postmottak-oppgaver").increment()
    MDC.putCloseable("journalpostId", request.journalpostId.toString()).use {
        dataSource.transaction { connection ->
            OppdaterOppgaveService(
                msGraphClient,
                oppgaveRepository = OppgaveRepository(connection),
                flytJobbRepository = FlytJobbRepositoryImpl(connection),
                mottattDokumentRepository = MottattDokumentRepository(connection),
                tilbakekrevingRepository = TilbakekrevingRepository(connection),
            ).håndterNyOppgaveOppdatering(request.tilOppgaveOppdatering())
        }
    }

    respondWithStatus(HttpStatusCode.OK)
}

fun NormalOpenAPIRoute.oppdaterTilbakekrevingOppgaverApi(
    dataSource: DataSource,
    msGraphClient: IMsGraphGateway,
    prometheus: PrometheusMeterRegistry
) = route("/oppdater-tilbakekreving-oppgaver").authorizedPost<Unit, Unit, TilbakekrevingsbehandlingOppdatertHendelse>(
    routeConfig = AuthorizationBodyPathConfig(
        operasjon = Operasjon.SAKSBEHANDLE,
        applicationsOnly = true,
        applicationRole = "oppdater-behandlingsflyt-oppgaver"
    )
) { _, request ->
    prometheus.httpCallCounter("/oppdater-tilbakekreving-oppgaver").increment()
    LoggerFactory.getLogger("tilbakekreving")
        .info("Mottatt melding om oppdatering av oppgave med tilbakekrevingsbehandling, saksnummer: ${request.saksnummer}")
    MDC.putCloseable("saksnummer", request.saksnummer.toString()).use {
        dataSource.transaction { connection ->
            OppdaterOppgaveService(
                msGraphClient,
                oppgaveRepository = OppgaveRepository(connection),
                flytJobbRepository = FlytJobbRepositoryImpl(connection),
                mottattDokumentRepository = MottattDokumentRepository(connection),
                tilbakekrevingRepository = TilbakekrevingRepository(connection),
            ).håndterNyOppgaveOppdatering(request.tilOppgaveOppdatering())
        }
    }
    respondWithStatus(HttpStatusCode.OK)
}
