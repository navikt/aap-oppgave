package no.nav.aap.oppgave.statistikk

import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDateTime
import java.util.*

enum class HendelseType {
    OPPRETTET,
    OPPDATERT,
    RESERVERT,
    AVRESERVERT,
    LUKKET
}

data class OppgaveHendelse(
    val hendelse: HendelseType,
    val oppgaveTilStatistikkDto: OppgaveTilStatistikkDto
)

/**
 * Denne DTO-en brukes for å sende oppgavehendelser i statistikk.
 * Den inneholder kun de feltene som statistikk-appen trenger.
 *
 * @param enhet Den faktiske enheten som skal utføre oppgaven.
 */
data class OppgaveTilStatistikkDto(
    val id: Long? = null,
    val personIdent: String? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val enhet: String,
    val avklaringsbehovKode: String,
    val status: Status = Status.OPPRETTET,
    val behandlingstype: Behandlingstype,
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
    val versjon: Long = 0
) {
    companion object {}
}