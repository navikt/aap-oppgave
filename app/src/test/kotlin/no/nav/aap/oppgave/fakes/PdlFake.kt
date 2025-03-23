package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.pdlFake() {
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        post("/graphql") {
            val body = call.receive<String>()
            if (body.contains("hentGeografiskTilknytning")) {
                call.respondText(genererHentAdressebeskytelseOgGeotilknytning())
            } else if (body.contains("hentPersonBolk")) {
                call.respondText(genererHentPersonBolkRespons(body))
            } else {
                call.respondText(genererHentPersonRespons())
            }
        }
    }
}

private fun genererHentAdressebeskytelseOgGeotilknytning(): String {
    return """
            {
              "data": {
                "hentPerson": {
                  "adressebeskyttelse": [ {"gradering": "UGRADERT"}]
                },
                "hentGeografiskTilknytning": {
                  "gtType": "KOMMUNE",
                  "gtKommune": "3207",
                  "gtBydel": null,
                  "gtLand": null
                }
              }
            }
        """.trimIndent()
}

private fun genererHentPersonBolkRespons(body: String): String {
    val identer = finnIdenterIBody(body)
    val resultat = identer.joinToString(", ") { ident ->
        """
        {
          "ident": "$ident",
          "person": {
            "adressebeskyttelse": ${finnGradering(ident)}
          },
          "code": "ok"
        }
    """.trimIndent()
    }
    return """
                    { "data":
                    {"hentPersonBolk": [$resultat]
                    }}
                """.trimIndent()
}

private fun genererHentPersonRespons(): String {
    return """
            { "data":
            {"hentPerson": {
                    "foedselsdato": [
                        {"foedselsdato": "1990-01-01"}
                    ]
                }
            }}
        """.trimIndent()
}

private fun finnIdenterIBody(body: String): List<String> {
    return body.substringAfter("\"identer\" :")
        .substringBefore("}")
        .substringBefore(",")
        .replace("[", "")
        .replace("]", "")
        .replace("\"", "")
        .split(",")
        .map { it.replace("\n", "").trim() }
        .filter { it != "null" }
}

private fun finnGradering(ident: String): String {
    return when (ident) {
        STRENGT_FORTROLIG_IDENT -> "[{\"gradering\": \"STRENGT_FORTROLIG\"}]"
        else -> "[{\"gradering\": \"UGRADERT\"}]"
    }
}

const val STRENGT_FORTROLIG_IDENT = "11111100000"

internal data class TestToken(
    val access_token: String,
    val refresh_token: String = "very.secure.token",
    val id_token: String = "very.secure.token",
    val token_type: String = "token-type",
    val scope: String? = null,
    val expires_in: Int = 3599,
)
