package no.nav.aap.oppgave.fakes

import io.ktor.server.application.Application
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking

class FakeServer(port: Int = 0, private val module: Application.() -> Unit) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = port, module = module).start()

    fun stop() {
        server.stop()
    }

    fun clean() {
        val port = server.port()
        server.stop(0, 0)
        server = embeddedServer(Netty, port = port, module = module).start()
    }

    fun setCustomModule(module: Application.() -> Unit) {
        val port = server.port()
        server.stop(0, 0)
        server = embeddedServer(Netty, port = port, module = module).start()
    }

    fun port(): Int = server.port()

    private fun EmbeddedServer<*, *>.port(): Int {
        return runBlocking {
            this@port.engine.resolvedConnectors()
        }.first { it.type == ConnectorType.HTTP }.port
    }
}