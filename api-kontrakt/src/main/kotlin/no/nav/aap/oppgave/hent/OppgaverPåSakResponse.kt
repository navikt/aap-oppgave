package no.nav.aap.oppgave.hent

import java.util.UUID

data class OppgaverPåSakResponse(
    val oppgaver: List<OppgavePåSakResponse>
)

data class OppgavePåSakResponse(
    val id: Long,
    val versjon: Long,
    val behandlingsreferanse: UUID,
    val reservertAvIdent: String?,
    val reservertAvNavn: String?,
)