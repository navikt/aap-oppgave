package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoData
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoRespons
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomRessurs
import no.nav.aap.oppgave.server.ErrorRespons
import kotlin.collections.emptyList

fun Application.nomAnsattInfoFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@nomAnsattInfoFake.log.info("Nom :: Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorRespons(cause.message)
            )
        }

    }

    routing {
        post() {
            val data = AnsattInfoData(
                NomRessurs(
                    visningsnavn = "Test Testesen"
                )
            )
            val response = AnsattInfoRespons(
                data,
                emptyList()
            )

            call.respond(response)
        }
    }
}