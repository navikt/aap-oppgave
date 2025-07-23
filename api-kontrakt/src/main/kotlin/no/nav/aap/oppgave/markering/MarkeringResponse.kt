package no.nav.aap.oppgave.markering

import no.nav.aap.oppgave.verdityper.MarkeringForBehandling

data class MarkeringResponse(
    val markeringType: MarkeringForBehandling,
    val begrunnelse: String,
    val opprettetAv: String
)