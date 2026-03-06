package no.nav.aap.oppgave.enhet

import java.time.LocalDate

data class PersonRequest(val ident: String)

enum class OppgaveKategori {
    MEDLEMSKAP,
    LOKALKONTOR,
    KVALITETSSIKRING,
    NAY,
    BESLUTTER
}

data class NåværendeEnhet(
    val oversendtDato: LocalDate,
    val løstDato: LocalDate? = null, // trengs denne?
    val oppgaveKategori: OppgaveKategori,
    val enhet: String,
)

data class EnhetOgEversendelse(
    val tilstand: NåværendeEnhet?
)


