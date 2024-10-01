package no.nav.aap.oppgave.server

import com.papsign.ktor.openapigen.route.apiRouting
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.server.authenticate.AZURE
import no.nav.aap.komponenter.commonKtorModule
import no.nav.aap.komponenter.dbmigrering.Migrering
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.oppgave.alleÅpneOppgaverApi
import no.nav.aap.oppgave.avreserverOppgave
import no.nav.aap.oppgave.filter.filterApi
import no.nav.aap.oppgave.flyttOppgave
import no.nav.aap.oppgave.hentOppgaveApi
import no.nav.aap.oppgave.mineOppgaverApi
import no.nav.aap.oppgave.oppdater.oppdaterOppgaverApi
import no.nav.aap.oppgave.oppdater.opprettOppgaveApi
import no.nav.aap.oppgave.plukk.plukkApi
import org.slf4j.LoggerFactory

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

    commonKtorModule(prometheus, AzureConfig(), "AAP - Oppgave")

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

    routing {
        authenticate(AZURE) {
            apiRouting {
                opprettOppgaveApi(dataSource, prometheus)
                oppdaterOppgaverApi(dataSource, prometheus)
                plukkApi(dataSource, prometheus)
                hentOppgaveApi(dataSource, prometheus)
                mineOppgaverApi(dataSource, prometheus)
                alleÅpneOppgaverApi(dataSource, prometheus)
                filterApi(dataSource, prometheus)
                avreserverOppgave(dataSource, prometheus)
                flyttOppgave(dataSource, prometheus)
            }
        }
        actuator(prometheus)
    }
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