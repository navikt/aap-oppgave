package no.nav.aap.oppgave.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.fakes.Fakes
import java.io.BufferedWriter
import java.io.FileWriter
import java.net.URI
import java.nio.charset.StandardCharsets


fun main() {
    val postgres = postgreSQLContainer()
    val fakes = Fakes()
    fakes.setProperties()
    val server = embeddedServer(Netty, port = 0) {
        val dbConfig = DbConfig(
            username = postgres.username,
            password = postgres.password,
            jdbcUrl = postgres.jdbcUrl,
        )
        server(dbConfig, PrometheusMeterRegistry(PrometheusConfig.DEFAULT))
        module(fakes)
    }.start()

    val client = RestClient.withDefaultResponseHandler(
        config = ClientConfig(scope = "oppgave"),
        tokenProvider = ClientCredentialsTokenProvider
    )

    val port = runBlocking { server.engine.resolvedConnectors().first { it.type == ConnectorType.HTTP }.port }

    val openApiDoc =
        requireNotNull(
            client.get(
                URI.create("http://localhost:$port/openapi.json"),
                GetRequest()
            ) { body, _ ->
                String(body.readAllBytes(), StandardCharsets.UTF_8)
            }
        )


    val writer = BufferedWriter(FileWriter("../openapi.json"))
    writer.use {
        it.write(openApiDoc)
    }

    server.stop(0, 0)
}