package no.nav.aap.oppgave.plukk

import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.util.UUID

data class PlukkOppgaveResponse(
    val behandlingsreferanse: UUID,
    val saksnummer: String?,
    val journalpostId: Long?,
    val behandlingstype: Behandlingstype,
    val tilbakekrevingUrl: String?,
)