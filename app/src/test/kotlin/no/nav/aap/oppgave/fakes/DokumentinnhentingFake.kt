package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.aap.dokumentinnhenting.kontrakt.PåminnelseInfoResponse


fun Application.dokumentinnhentingFake() {

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        get("/dialogmelding/paaminnelse/{behandlingsreferanse}/hent") {
            call.respond(
                PåminnelseInfoResponse(
                    påminnelser = emptyList(),
                )
            )
        }
    }

}