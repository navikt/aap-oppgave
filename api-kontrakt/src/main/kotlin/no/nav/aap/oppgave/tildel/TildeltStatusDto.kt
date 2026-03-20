package no.nav.aap.oppgave.tildel

data class TildeltStatusDto (
    val tildeltSaksbehandlerIdent: String?,
    val tildeltSaksbehandlerNavn: String?,
    val erTildeltInnloggetBruker: Boolean,
)

