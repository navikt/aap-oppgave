package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.aap.oppgave.server.ErrorRespons
import java.time.LocalDate

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
        post {
            val body = call.receive<String>()
            if (body.contains("navIdent")) {
                call.respondText(genererAnsattInfoRespons())
            } else if (body.contains("navidenter")) {
                call.respondText(genererAnsattInfoBatchRespons(body))
            } else {
                call.respond(HttpStatusCode.BadRequest, "Ugyldig request body")
            }
        }
    }
}

private fun genererAnsattInfoRespons(): String {
    return """
        {
            "data": {
                "ressurs": {
                    "visningsnavn": "Test Testesen"
                }
            }
        }
    """.trimIndent()
}

private fun genererAnsattInfoBatchRespons(body: String): String {
    val identer = finnIdenterIBody(body)
    val resultat = identer.joinToString(separator = ",") { ident ->

        if (ident == "12121212") {
            """
                {
                    "id": "$ident",
                    "code": "NOT_FOUND",
                    "ressurs": null
                }
            """.trimIndent()
        } else {
            val sluttdato = if (ident == "000011111") "\"${LocalDate.now().minusDays(10)}\"" else "null"
            """
            {
                "id": "$ident",
                "code": "OK",
                "ressurs": {
                    "navident": "$ident",
                    "sluttdato": $sluttdato
                }
            }
        """.trimIndent()
        }
    }

    return """
        {
            "data": {
                "ressurser": [
                    $resultat
                ]
            }
        }
    """.trimIndent()
}

private fun finnIdenterIBody(body: String): List<String> {
    // Regexen under matcher teksten mot teksten identer etterfult av whitespace og et kolon
    // Den har en en matchgroup som matcher som inneholder hele "arrayet".
    val identerPattern = "\"navidenter\"\\s*:\\s*(\\[[^]]*])".toRegex()
    val identerMatch = identerPattern.find(body) ?: return emptyList()
    val identerArray = identerMatch.groupValues[1]

    val identPattern = "\"([^\"]+)\"".toRegex()
    return identPattern.findAll(identerArray)
        .map { it.groupValues[1] }
        .filter { it != "null" }
        .toList()
}
