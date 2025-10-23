package no.nav.aap.oppgave.oppdater

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.tilgang.AuthorizationBodyPathConfig
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.authorizedPost
import javax.sql.DataSource

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
    dataSource.transaction { connection ->
        OppdaterOppgaveService(
            msGraphClient,
            oppgaveRepository = OppgaveRepository(connection),
            flytJobbRepository = FlytJobbRepositoryImpl(connection),
            mottattDokumentRepository = MottattDokumentRepository(connection),
        ).håndterNyOppgaveOppdatering(
            request.tilOppgaveOppdatering()
        )
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
    dataSource.transaction { connection ->
        OppdaterOppgaveService(
            msGraphClient,
            oppgaveRepository = OppgaveRepository(connection),
            flytJobbRepository = FlytJobbRepositoryImpl(connection),
            mottattDokumentRepository = MottattDokumentRepository(connection),
        ).håndterNyOppgaveOppdatering(request.tilOppgaveOppdatering())
    }
    respondWithStatus(HttpStatusCode.OK)
}
