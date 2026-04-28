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
    val oppgaveKategori: OppgaveKategori,
    val enhet: String,
    val markertSomHasteSak: Boolean,
    val saksnummer: String
)

data class EnhetOgOversendelse(
    val tilstand: NåværendeEnhet?
)


