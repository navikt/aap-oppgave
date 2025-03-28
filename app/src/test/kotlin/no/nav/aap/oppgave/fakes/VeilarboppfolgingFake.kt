package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.veilarboppfolgingFake() {

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        post("/veilarboppfolging/api/v3/hent-veileder") {
            call.respondText(responsFraVeilarboppfolging())
        }
    }

}

private fun responsFraVeilarboppfolging(): String {
    return """
            { 
                "veilederIdent": "ident"
           
            }
    """.trimIndent()
}
