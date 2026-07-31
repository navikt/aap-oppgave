package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.ReturStatus
import no.nav.aap.oppgave.verdityper.ÅrsakTilReturKode

data class ReturInformasjonDto(
    val status: ReturStatus,
    val årsaker: List<ÅrsakTilReturKode>,
    val begrunnelse: String,
    val endretAv: String,
)