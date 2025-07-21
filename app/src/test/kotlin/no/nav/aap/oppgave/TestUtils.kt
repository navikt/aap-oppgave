package no.nav.aap.oppgave

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.runBlocking

fun EmbeddedServer<*, *>.port(): Int =
    runBlocking { this@port.engine.resolvedConnectors() }
        .first { it.type == ConnectorType.HTTP }
        .port