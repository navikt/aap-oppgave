package no.nav.aap.oppgave.fakes

import com.fasterxml.jackson.databind.JsonNode
import com.nimbusds.jwt.JWTParser
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
import no.nav.aap.oppgave.server.ErrorRespons

fun Application.texasFake() {
    install(ContentNegotiation) {
        jackson()
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@texasFake.log.info("TEXAS :: Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorRespons(cause.message)
            )
        }
    }
    routing {
        post("/token") {
            val token = AzureTokenGen(
                issuer = "entra_id",
                audience = "oppgave"
            ).generate(isApp = true)
            call.respond(TestToken(access_token = token))
        }

        post("/token/exchange") {
            val body = call.receive<JsonNode>()
            val claims = JWTParser.parse(body["user_token"].asText())
                .jwtClaimsSet

            val token = AzureTokenGen(
                issuer = "oppgave",
                audience = body["target"].asText(),

            ).generate(
                isApp = false,
                roller = claims.getStringListClaim("groups") ?: emptyList(),
                navIdent = claims.getClaimAsString("NAVident")
            )
            call.respond(TestToken(access_token = token))
        }

        post("/introspect") {
            call.respond(mapOf("active" to true))
        }
    }
}
