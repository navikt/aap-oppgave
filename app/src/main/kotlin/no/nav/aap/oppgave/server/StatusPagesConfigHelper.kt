package no.nav.aap.oppgave.server

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import java.net.http.HttpTimeoutException
import java.sql.SQLException
import no.nav.aap.komponenter.httpklient.exception.ApiErrorCode
import no.nav.aap.komponenter.httpklient.exception.ApiException
import no.nav.aap.komponenter.httpklient.exception.InternfeilException
import no.nav.aap.oppgave.FeilVersjonException
import org.slf4j.LoggerFactory

object StatusPagesConfigHelper {
    fun setup(): StatusPagesConfig.() -> Unit = {
        exception<Throwable> { call, cause ->
            val uri = call.request.local.uri
            val logger = LoggerFactory.getLogger(javaClass)

            when (cause) {
                is ClientRequestException -> {
                    if (cause.response.status == HttpStatusCode.RequestTimeout) {
                        logger.warn("Timeout ved kall til '$uri'", cause)
                        call.respondWithError(
                            ApiException(
                                status = HttpStatusCode.RequestTimeout,
                                message = "Forespørselen tok for lang tid. Prøv igjen om litt."
                            )
                        )
                    } else {
                        logger.error("Feil ved kall til '$uri'.", cause)
                        call.respondWithError(
                            ApiException(
                                status = cause.response.status,
                                message = "Feil ved kall til '$uri'."
                            )
                        )
                    }
                }

                is HttpRequestTimeoutException,
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

                is SQLException -> {
                    logger.error("SQL-feil av type '${cause.javaClass.name}'. Se sikker logs for flere detaljer.")
                    secureLogger.error("SQL-feil: ", cause)

                    call.respondWithError(InternfeilException("En feil oppsto. Prøv igjen om litt."))
                }

                is FeilVersjonException -> {
                    val msg = "Endring av ${cause.oppgaveId} feilet siden faktisk versjon er ${cause.faktiskVersjon}"
                    logger.info(msg, cause)
                    /** Beholder observerbar adferd ved å returnere InternfeilException siden jeg ikke
                     * har undersøkt feilhåndtering hos klienter. Ser for meg at dette er en type feil som frontend
                     * kanskje kunne håndtert spesielt.
                     */
                    call.respondWithError(InternfeilException(msg))
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