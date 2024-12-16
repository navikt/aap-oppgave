package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val erEgenansatt: suspend RoutingContext.() -> Unit = {
    call.respond(true)
}

val erIkkeEgenansatt: suspend RoutingContext.() -> Unit = {
    call.respond(false)
}

fun Application.nomFake(
    egenansatt: suspend RoutingContext.() -> Unit = erIkkeEgenansatt,
) {

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        post("/skjermet", egenansatt)
    }

}