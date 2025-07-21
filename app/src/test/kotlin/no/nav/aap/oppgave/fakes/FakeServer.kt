package no.nav.aap.oppgave.fakes

import io.ktor.server.application.Application
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.runBlocking
import no.nav.aap.oppgave.port

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
}