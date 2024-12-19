package no.nav.aap.oppgave.server

import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbmigrering.Migrering
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.motor.Motor
import no.nav.aap.motor.api.motorApi
import no.nav.aap.oppgave.alleÅpneOppgaverApi
import no.nav.aap.oppgave.avreserverOppgave
import no.nav.aap.oppgave.enhet.hentEnhetApi
import no.nav.aap.oppgave.filter.hentFilterApi
import no.nav.aap.oppgave.filter.opprettEllerOppdaterFilterApi
import no.nav.aap.oppgave.filter.slettFilterApi
import no.nav.aap.oppgave.flyttOppgave
import no.nav.aap.oppgave.hentOppgaveApi
import no.nav.aap.oppgave.hentOppgavelisteApi
import no.nav.aap.oppgave.hentOppgaverApi
import no.nav.aap.oppgave.klienter.msgraph.MsGraphClient
import no.nav.aap.oppgave.mineOppgaverApi
import no.nav.aap.oppgave.oppdater.oppdaterBehandlingOppgaverApi
import no.nav.aap.oppgave.oppdater.oppdaterPostmottakOppgaverApi
import no.nav.aap.oppgave.oppgavesøkApi
import no.nav.aap.oppgave.plukk.plukkNesteApi
import no.nav.aap.oppgave.plukk.plukkOppgaveApi
import no.nav.aap.oppgave.produksjonsstyring.hentAntallOppgaver
import no.nav.aap.oppgave.prosessering.StatistikkHendelseJobb
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val SECURE_LOGGER = LoggerFactory.getLogger("secureLog")
private const val ANTALL_WORKERS = 5

class App

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        LoggerFactory.getLogger("App")
            .error("Ikke-håndert exception: ${e::class.qualifiedName}. Se sikker logg for stacktrace")
        SECURE_LOGGER.error("Uhåndtert feil", e)
    }
    embeddedServer(Netty, 8080) { server(DbConfig()) }.start(wait = true)
}

internal fun Application.server(dbConfig: DbConfig) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    commonKtorModule(
        prometheus, AzureConfig(), InfoModel(
            title = "AAP - Oppgave",
            description = """
                For å teste API i dev, besøk
                <a href="https://azure-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:aap:oppgave">Token Generator</a> for å få token.
                """.trimIndent(),
        )
    )

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            LoggerFactory.getLogger(App::class.java)
                .warn("Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
            call.respond(status = HttpStatusCode.Companion.InternalServerError, message = ErrorRespons(cause.message))
        }
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    val dataSource = initDatasource(dbConfig)
    Migrering.migrate(dataSource)

    val iMsGraphClient = MsGraphClient(AzureConfig())

    val motor = motor(dataSource)

    routing {
        authenticate(AZURE) {
            apiRouting {
                // Oppdater oppgaver fra applikasjonene
                oppdaterBehandlingOppgaverApi(dataSource, prometheus)
                oppdaterPostmottakOppgaverApi(dataSource, prometheus)
                // Plukk/endre oppgave
                plukkNesteApi(dataSource, prometheus)
                plukkOppgaveApi(dataSource, prometheus)
                avreserverOppgave(dataSource, prometheus)
                flyttOppgave(dataSource, prometheus)
                // Hent oppgave(r)
                hentOppgaveApi(dataSource, prometheus)
                hentOppgaverApi(dataSource, prometheus)
                mineOppgaverApi(dataSource, prometheus)
                alleÅpneOppgaverApi(dataSource, prometheus)
                hentOppgavelisteApi(dataSource, prometheus)
                oppgavesøkApi(dataSource, prometheus)
                // Filter
                hentFilterApi(dataSource, prometheus)
                opprettEllerOppdaterFilterApi(dataSource, prometheus)
                slettFilterApi(dataSource, prometheus)
                // Produksjonsstyring
                hentAntallOppgaver(dataSource, prometheus)
                // Enheter
                hentEnhetApi(iMsGraphClient, prometheus)
                // Motor-API
                motorApi(dataSource)
            }
        }
        actuator(prometheus)
    }
}

fun Application.motor(dataSource: DataSource): Motor {
    val motor = Motor(
        dataSource = dataSource,
        antallKammer = ANTALL_WORKERS,
        jobber = listOf(StatistikkHendelseJobb)
    )

    monitor.subscribe(ApplicationStarted) {
        motor.start()
    }

    monitor.subscribe(ApplicationStopPreparing) { application ->
        application.log.info("Server er i ferd med å stoppe")
        motor.stop()
        // Release resources and unsubscribe from events
        monitor.unsubscribe(ApplicationStopPreparing) {}
        monitor.unsubscribe(ApplicationStopPreparing) {}
    }

    return motor
}

class DbConfig(
    val database: String = System.getenv("NAIS_DATABASE_OPPGAVE_OPPGAVE_DATABASE"),
    val jdbcUrl: String = System.getenv("NAIS_DATABASE_OPPGAVE_OPPGAVE_JDBC_URL"),
    val username: String = System.getenv("NAIS_DATABASE_OPPGAVE_OPPGAVE_USERNAME"),
    val password: String = System.getenv("NAIS_DATABASE_OPPGAVE_OPPGAVE_PASSWORD")
)

fun initDatasource(dbConfig: DbConfig) = HikariDataSource(HikariConfig().apply {
    jdbcUrl = dbConfig.jdbcUrl
    username = dbConfig.username
    password = dbConfig.password
    maximumPoolSize = 10 + ANTALL_WORKERS
    minimumIdle = 1
    driverClassName = "org.postgresql.Driver"
    connectionTestQuery = "SELECT 1"
})

internal data class ErrorRespons(val message: String?)

private fun Routing.actuator(prometheus: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metrics") {
            call.respond(prometheus.scrape())
        }

        get("/live") {
            val status = HttpStatusCode.Companion.OK
            call.respond(status, "Oppe!")
        }

        get("/ready") {
            val status = HttpStatusCode.Companion.OK
            call.respond(status, "Oppe!")
        }
    }
}