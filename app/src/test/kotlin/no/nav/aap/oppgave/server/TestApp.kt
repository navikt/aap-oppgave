package no.nav.aap.oppgave.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.oppgave.fakes.Fakes
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit

fun main() {
    val postgres = postgreSQLContainer()
    val fakes = Fakes().also { it.beforeAll(null) }
    embeddedServer(Netty, port = 8084) {
        val dbConfig = DbConfig(
            username = postgres.username,
            password = postgres.password,
            jdbcUrl = postgres.jdbcUrl,
        )
        server(dbConfig, PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
        module(fakes)
    }.start(wait = true)
}

internal fun postgreSQLContainer(): PostgreSQLContainer<Nothing> {
    val postgres = PostgreSQLContainer<Nothing>("postgres:16")
    postgres.waitingFor(HostPortWaitStrategy().withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS)))
    postgres.start()
    println("\n\n${postgres.jdbcUrl}&user=test&password=test\n\n")
    return postgres
}

internal fun Application.module(fakes: Fakes) {
    // Setter opp virtuell sandkasse lokalt
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server har stoppet")
        fakes.close()
        // Release resources and unsubscribe from events
        application.monitor.unsubscribe(ApplicationStopped) {}
    }
}