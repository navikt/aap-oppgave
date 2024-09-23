package no.nav.aap.oppgave

data class FlyttOppgaveDto(
    val avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
    val flyttTilIdent: String
)