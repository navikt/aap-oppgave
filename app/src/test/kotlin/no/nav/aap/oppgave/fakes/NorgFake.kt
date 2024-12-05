package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val finnEnhet: suspend RoutingContext.() -> Unit = {
    call.respond("""[{"enhetNr": "superNav!"}]""")
}

fun Application.norgFake(
    bestMAtch: suspend RoutingContext.() -> Unit = finnEnhet,
) {

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        post("/norg2/api/v1/arbeidsfordeling/enheter/bestmatch", bestMAtch)
    }

}
