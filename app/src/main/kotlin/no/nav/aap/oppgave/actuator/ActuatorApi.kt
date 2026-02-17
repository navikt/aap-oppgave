package no.nav.aap.oppgave.actuator

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.oppgave.server.ReadinessState

fun Routing.actuatorApi(prometheus: PrometheusMeterRegistry) {
    route("/actuator") {
        get("/metrics") {
            call.respond(prometheus.scrape())
        }

        get("/live") {
            call.respond(HttpStatusCode.OK, "Alive")
        }

        get("/ready") {
            if (ReadinessState.isReady()) {
                call.respond(HttpStatusCode.OK, "Ready")
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, "Not ready")
            }
        }
    }
}