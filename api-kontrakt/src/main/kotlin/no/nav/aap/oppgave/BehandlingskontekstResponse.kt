package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.util.UUID

data class BehandlingskontekstResponse(
    val behandlingsreferanse: UUID,
    val journalpostId: Long?,
    val saksnummer: String?,
    val behandlingstype: Behandlingstype,
    val tilbakekrevingUrl: String?,
)