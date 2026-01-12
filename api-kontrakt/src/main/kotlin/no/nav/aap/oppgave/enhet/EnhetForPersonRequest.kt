package no.nav.aap.oppgave.enhet

data class EnhetForPersonRequest(
    val personIdent: String,
    val relevanteIdenter: List<String>,
)