package no.nav.aap.oppgave.tildel

data class SaksbehandlerSøkResponse(
    val saksbehandlere: List<SaksbehandlerDto>
)

data class SaksbehandlerDto(
    val navn: String? = null,
    val navIdent: String,
)
