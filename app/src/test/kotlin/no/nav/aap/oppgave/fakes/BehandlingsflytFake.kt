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
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import no.nav.aap.oppgave.klienter.behandlingsflyt.IdenterRespons
import no.nav.aap.oppgave.server.ErrorRespons

fun Application.behandlingsflytFake(fakesConfig: FakesConfig) =
    runBlocking {
        install(ContentNegotiation) {
            jackson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                this@behandlingsflytFake.log.info(
                    "BEHANDLINGSFLYT :: Ukjent feil ved kall til '{}'",
                    call.request.local.uri,
                    cause
                )
                call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
            }
        }
        routing {
            get("/pip/api/behandling/{behandlingsreferanse}/identer") {
                call.respond(IdenterRespons(søker = emptyList(), barn = fakesConfig.relaterteIdenterPåBehandling))
            }
        }
    }