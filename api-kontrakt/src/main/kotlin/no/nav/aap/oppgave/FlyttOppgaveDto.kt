package no.nav.aap.oppgave

data class FlyttOppgaveDto(
    val oppgaveReferanseDto: OppgaveReferanseDto,
    val flyttTilIdent: String
)