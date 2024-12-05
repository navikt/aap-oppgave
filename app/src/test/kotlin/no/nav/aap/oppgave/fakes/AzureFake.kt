package no.nav.aap.oppgave.fakes

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.aap.oppgave.server.ErrorRespons

fun Application.azureFake() {
    install(ContentNegotiation) {
        jackson()
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@azureFake.log.info("AZURE :: Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
            call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
        }
    }
    routing {
        post("/token") {
            val token = AzureTokenGen("behandlingsflyt", "behandlingsflyt").generate()
            call.respond(TestToken(access_token = token))
        }
        get("/jwks") {
            call.respond(AZURE_JWKS)
        }
    }
}