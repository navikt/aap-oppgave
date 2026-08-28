package uføreVedtak

import no.nav.aap.oppgave.verdityper.UføreVedtakStatus
import java.time.LocalDate

class UføreVedtak (
    val virkningsdato: LocalDate,
    val status: UføreVedtakStatus
)