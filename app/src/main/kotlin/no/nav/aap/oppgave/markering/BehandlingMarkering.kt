package no.nav.aap.oppgave.markering

import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import java.time.LocalDateTime

data class BehandlingMarkering(
    val markeringType: MarkeringForBehandling,
    val begrunnelse: String? = null,
    val opprettetAv: String,
    val opprettetAvNavn: String? = null,
    val opprettetTidspunkt: LocalDateTime? = null
)