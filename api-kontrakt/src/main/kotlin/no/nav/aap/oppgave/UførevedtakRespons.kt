package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.UføreVedtakStatus
import java.time.LocalDate

class UførevedtakRespons (
    val virkningsdato: LocalDate,
    val resultat: UføreVedtakStatus
)