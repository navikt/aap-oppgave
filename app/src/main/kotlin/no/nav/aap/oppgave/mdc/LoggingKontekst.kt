package no.nav.aap.oppgave.mdc

import org.slf4j.MDC
import java.io.Closeable

data class LoggingKontekst(
    val saksnummer: String? = null,
    val journalpostId: String? = null,
) : Closeable {
    init {
        if (saksnummer != null) {
            MDC.put("saksnummer", saksnummer)
        }
        if (journalpostId != null) {
            MDC.put("journalpostId", journalpostId)
        }
    }

    override fun close() {
        if (saksnummer != null) {
            MDC.remove("saksnummer")
        }
        if (journalpostId != null) {
            MDC.remove("journalpostId")
        }
    }
}