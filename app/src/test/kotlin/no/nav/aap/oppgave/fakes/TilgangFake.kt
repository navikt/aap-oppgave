package no.nav.aap.oppgave.fakes

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import no.nav.aap.oppgave.server.ErrorRespons
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.TilgangResponse

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
            if (req.behandlingsreferanse in fakesConfig.negativtSvarFraTilgangForBehandling)  {
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