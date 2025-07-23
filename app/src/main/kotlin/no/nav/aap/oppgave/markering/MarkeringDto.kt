package no.nav.aap.oppgave.markering

import no.nav.aap.oppgave.MarkeringForBehandling

data class MarkeringDto(
    val type: MarkeringForBehandling,
    val begrunnelse: String
)