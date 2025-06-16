package no.nav.aap.oppgave

data class SøkResponse(
    val oppgaver: List<OppgaveDto>,
    val harTilgang: Boolean,
)