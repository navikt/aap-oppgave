package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.veilarbarenaFake() {

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        post("/veilarbarena/api/v2/arena/hent-status") {
            call.respondText(responsFraArena())
        }
    }

}

private fun responsFraArena(): String {
    return """
            { 
                "oppfolgingsenhet": "superNav!"
           
            }
    """.trimIndent()
}
