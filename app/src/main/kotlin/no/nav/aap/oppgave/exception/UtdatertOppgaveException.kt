package no.nav.aap.oppgave.exception

import io.ktor.http.HttpStatusCode
import no.nav.aap.komponenter.httpklient.exception.ApiException

class UtdatertOppgaveException(feilmelding: String) :
    ApiException(status = HttpStatusCode.Conflict, message = feilmelding)