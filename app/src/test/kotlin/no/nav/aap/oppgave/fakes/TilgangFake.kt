package no.nav.aap.oppgave.fakes

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import no.nav.aap.oppgave.server.ErrorRespons
import tilgang.BehandlingTilgangRequest
import tilgang.TilgangResponse
import java.util.UUID

fun Application.tilgangFake(fakesConfig: FakesConfig) = runBlocking {
    install(ContentNegotiation) {
        jackson()
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@tilgangFake.log.info(
                "TILGANG :: Ukjent feil ved kall til '{}'",
                call.request.local.uri,
                cause
            )
            call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
        }
    }
    routing {
        post("/tilgang/behandling") {
            val req = call.receive<BehandlingTilgangRequest>()
            if (UUID.fromString(req.behandlingsreferanse) in fakesConfig.negativtSvarFraTilgangForBehandling)  {
                call.respond(TilgangResponse(false))
            } else {
                call.respond(TilgangResponse(true))
            }
        }
        post("/tilgang/journalpost") {
            call.respond(TilgangResponse(true))
        }
    }
}