package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.UføreVedtakStatus
import java.time.LocalDate
import java.util.UUID

class UførevedtakRespons (
    val referanse: UUID,
    val virkningsdato: LocalDate,
    val resultat: UføreVedtakStatus
)