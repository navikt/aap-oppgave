package no.nav.aap.oppgave.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import no.nav.aap.komponenter.httpklient.exception.ApiErrorCode
import no.nav.aap.komponenter.httpklient.exception.ApiException
import no.nav.aap.komponenter.httpklient.exception.InternfeilException
import org.slf4j.LoggerFactory
import java.net.http.HttpTimeoutException

object StatusPagesConfigHelper {
    fun setup(): StatusPagesConfig.() -> Unit = {
        exception<Throwable> { call, cause ->
            val uri = call.request.local.uri
            val logger = LoggerFactory.getLogger(javaClass)

            when (cause) {
                is HttpTimeoutException -> {
                    logger.warn("Timeout mot $uri: ", cause)
                    call.respondWithError(
                        ApiException(
                            status = HttpStatusCode.RequestTimeout,
                            message = "Forespørselen tok for lang tid. Prøv igjen om litt."
                        )
                    )
                }

                is InternfeilException -> {
                    logger.error(cause.cause?.message ?: cause.message)
                    call.respondWithError(cause)
                }

                is ApiException -> {
                    logger.warn(cause.message, cause)
                    call.respondWithError(cause)
                }

                else -> {
                    logger.error(
                        "Ukjent feil ved kall til $uri. Type: ${cause.javaClass}. Message: ${cause.message}",
                        cause
                    )
                    call.respondWithError(InternfeilException("En ukjent feil oppsto"))
                }
            }
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            val uri = call.request.local.uri
            call.application.log.error("Fikk kall mot endepunkt som ikke finnes: $uri")

            call.respondWithError(
                ApiException(
                    status = HttpStatusCode.NotFound,
                    message = "Kunne ikke nå endepunkt: $uri",
                    code = ApiErrorCode.ENDEPUNKT_IKKE_FUNNET
                )
            )
        }
    }

    private suspend fun ApplicationCall.respondWithError(exception: ApiException) {
        respond(
            exception.status,
            exception.tilApiErrorResponse()
        )
    }
}