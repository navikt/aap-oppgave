package no.nav.aap.oppgave.server

import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import javax.sql.DataSource
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbmigrering.Migrering
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.komponenter.server.plugins.NavIdentInterceptor
import no.nav.aap.motor.Motor
import no.nav.aap.motor.api.motorApi
import no.nav.aap.motor.retry.RetryService
import no.nav.aap.oppgave.actuator.actuatorApi
import no.nav.aap.oppgave.avreserverOppgave
import no.nav.aap.oppgave.drift.driftApi
import no.nav.aap.oppgave.enhet.hentEnhetApi
import no.nav.aap.oppgave.enhet.nayEnhetForPerson
import no.nav.aap.oppgave.enhet.synkroniserEnhetPåOppgaveApi
import no.nav.aap.oppgave.filter.hentFilterApi
import no.nav.aap.oppgave.filter.opprettEllerOppdaterFilterApi
import no.nav.aap.oppgave.filter.slettFilterApi
import no.nav.aap.oppgave.klienter.msgraph.MsGraphGateway
import no.nav.aap.oppgave.markering.markeringApi
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.mottattdokument.mottattDokumentApi
import no.nav.aap.oppgave.oppdater.oppdaterBehandlingOppgaverApi
import no.nav.aap.oppgave.oppdater.oppdaterPostmottakOppgaverApi
import no.nav.aap.oppgave.oppdater.oppdaterTilbakekrevingOppgaverApi
import no.nav.aap.oppgave.oppgaveliste.hentOppgaveApi
import no.nav.aap.oppgave.oppgaveliste.hentOppgaveEnhetApi
import no.nav.aap.oppgave.oppgaveliste.mineOppgaverApi
import no.nav.aap.oppgave.oppgaveliste.oppgavelisteApi
import no.nav.aap.oppgave.oppgaveliste.oppgavesøkApi
import no.nav.aap.oppgave.oppgaveliste.søkApi
import no.nav.aap.oppgave.plukk.plukkOppgaveApi
import no.nav.aap.oppgave.produksjonsstyring.hentAntallOppgaver
import no.nav.aap.oppgave.prosessering.OppdaterOppgaveEnhetJobb
import no.nav.aap.oppgave.prosessering.StatistikkHendelseJobb
import no.nav.aap.oppgave.prosessering.VarsleOmOppgaverIkkeEndretJobb
import no.nav.aap.oppgave.tildel.tildelOppgaveApi
import org.slf4j.LoggerFactory

val secureLogger = LoggerFactory.getLogger("team-logs")
private const val ANTALL_WORKERS = 5

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        LoggerFactory.getLogger("App")
            .error("Ikke-håndert exception: ${e::class.qualifiedName}. Se sikker logg for stacktrace")
        secureLogger.error("Uhåndtert feil", e)
    }
    val serverPort = System.getenv("HTTP_PORT")?.toInt() ?: 8080
    embeddedServer(Netty, serverPort) { server(DbConfig(), prometheus) }.start(wait = true)
}

internal fun Application.server(dbConfig: DbConfig, prometheus: PrometheusMeterRegistry) {

    commonKtorModule(
        prometheus, AzureConfig(), InfoModel(
            title = "AAP - Oppgave",
            description = """
                For å teste API i dev, besøk
                <a href="https://azure-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:aap:oppgave">Token Generator</a> for å få token.
                """.trimIndent(),
        )
    )

    install(StatusPages, StatusPagesConfigHelper.setup())

    val dataSource = initDatasource(dbConfig, prometheus)
    Migrering.migrate(dataSource)

    val iMsGraphClient = MsGraphGateway(prometheus)

    motor(dataSource, prometheus)

    routing {
        authenticate(AZURE) {
            install(NavIdentInterceptor)

            apiRouting {
                // Oppdater oppgaver fra applikasjonene
                oppdaterBehandlingOppgaverApi(dataSource, iMsGraphClient, prometheus)
                oppdaterPostmottakOppgaverApi(dataSource, iMsGraphClient, prometheus)
                oppdaterTilbakekrevingOppgaverApi(dataSource, iMsGraphClient, prometheus)
                // Plukk/endre oppgave
                plukkOppgaveApi(dataSource, prometheus)
                avreserverOppgave(dataSource, prometheus)
                mottattDokumentApi(dataSource, prometheus)
                tildelOppgaveApi(dataSource, prometheus)
                // Hent oppgave(r)
                hentOppgaveApi(dataSource, prometheus)
                oppgavelisteApi(dataSource, prometheus)
                hentOppgaveEnhetApi(dataSource, prometheus)
                oppgavesøkApi(dataSource, prometheus)
                mineOppgaverApi(dataSource, prometheus)
                søkApi(dataSource, prometheus)
                markeringApi(dataSource, prometheus)
                // Filter
                hentFilterApi(dataSource, prometheus)
                opprettEllerOppdaterFilterApi(dataSource, prometheus)
                slettFilterApi(dataSource, prometheus)
                // Produksjonsstyring
                hentAntallOppgaver(dataSource, prometheus)
                // Enheter
                hentEnhetApi(iMsGraphClient, prometheus)
                nayEnhetForPerson(iMsGraphClient, prometheus)
                synkroniserEnhetPåOppgaveApi(dataSource, iMsGraphClient, prometheus)
                // Motor-API
                motorApi(dataSource)
                // Drifts-API
                driftApi(dataSource)
            }
        }
        actuatorApi(prometheus)
    }
}

fun Application.motor(dataSource: DataSource, prometheus: MeterRegistry): Motor {
    val motor = Motor(
        dataSource = dataSource,
        antallKammer = ANTALL_WORKERS,
        prometheus = prometheus,
        jobber = listOf(
            StatistikkHendelseJobb,
            OppdaterOppgaveEnhetJobb,
            VarsleOmOppgaverIkkeEndretJobb
        )
    )

    dataSource.transaction { dbConnection ->
        RetryService(dbConnection).enable()
    }

    monitor.subscribe(ApplicationStarted) {
        motor.start()
    }

    monitor.subscribe(ApplicationStopped) { application ->
        application.log.info("Server har stoppet")
        motor.stop()

        monitor.unsubscribe(ApplicationStarted) {}
        monitor.unsubscribe(ApplicationStopped) {}
    }

    return motor
}

class DbConfig(
    val jdbcUrl: String = System.getenv("NAIS_DATABASE_OPPGAVE_OPPGAVE_JDBC_URL"),
    val username: String = System.getenv("NAIS_DATABASE_OPPGAVE_OPPGAVE_USERNAME"),
    val password: String = System.getenv("NAIS_DATABASE_OPPGAVE_OPPGAVE_PASSWORD")
)

fun initDatasource(dbConfig: DbConfig, meterRegistry: MeterRegistry) = HikariDataSource(HikariConfig().apply {
    jdbcUrl = dbConfig.jdbcUrl
    username = dbConfig.username
    password = dbConfig.password
    maximumPoolSize = 10 + ANTALL_WORKERS
    minimumIdle = 1
    driverClassName = "org.postgresql.Driver"
    connectionTestQuery = "SELECT 1"
    metricRegistry = meterRegistry
})

internal data class ErrorRespons(val message: String?)
