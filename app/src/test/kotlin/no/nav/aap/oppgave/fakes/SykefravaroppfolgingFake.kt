package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.sykefravaroppfolgingFake() {

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        get("/api/v1/system/persontildeling/personer/single") {
            call.respondText(responsFraSykefravaroppfolging())
        }
    }

}

private fun responsFraSykefravaroppfolging(): String {
    return """
            { 
                "personident": { 
                    "value": "ident" 
                },
                "tildeltVeilederident": "ident",
                "tildeltEnhet": "1234"
            }
    """.trimIndent()
}
