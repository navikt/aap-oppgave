package uføreVedtak

import no.nav.aap.oppgave.UførevedtakRespons
import no.nav.aap.oppgave.verdityper.UføreVedtakStatus
import java.time.LocalDate

class UføreVedtak (
    val virkningsdato: LocalDate,
    val status: UføreVedtakStatus
)

fun UføreVedtak.tilUføreVedtakRepsons() : UførevedtakRespons {
    return UførevedtakRespons(
        virkningsdato = virkningsdato,
        resultat = status
    )
}