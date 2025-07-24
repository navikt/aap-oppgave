package no.nav.aap.oppgave.markering

import no.nav.aap.oppgave.verdityper.MarkeringForBehandling

data class MarkeringDto(
    val markeringType: MarkeringForBehandling,
    val begrunnelse: String,
    val opprettetAv: String? = null,
)