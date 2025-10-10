package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.msGraphFake() {

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
        }
    }

    routing {
        get("/me/memberOf") {
            call.respondText(responsFraMsGraph())
        }

        get("/groups/{groupId}/members") {
            call.respondText(responsFraMsGraphGroupMembers())
        }

        get("/groups") {
            call.respondText(responsFraMsGraphGroupSearch())
        }

    }

}

private fun responsFraMsGraph(): String {
    return """
            { 
                "value": [
                    { 
                        "id": "00000000-0000-4000-8000-000000000001",
                        "displayName": "0000-GA-ENHET_superNav!",
                        "mailNickname": "superNav!"
                    }
                ]
            }
    """.trimIndent()
}

private fun responsFraMsGraphGroupMembers(): String {
    // returnerer en veileder og en saksbehandler
    return """
        { 
                "value": [
                    {
                        "onPremisesSamAccountName": "KontorVeileder123",
                        "givenName": "Test",
                        "surname": "Kontorsen"
                    },
                    {
                        "onPremisesSamAccountName": "NayVeileder123",
                        "givenName": "Test",
                        "surname": "Naysen"
                    }
                ]
            }
    """.trimIndent()
}

private fun responsFraMsGraphGroupSearch(): String {
    return """
        { 
                "value": [
                    {
                        "id": "00000000-0000-4000-8000-000000000001",
                        "mailNickname": "0000-GA-ENHET_superNav!"
                    }
                ]
            }
    """
}
