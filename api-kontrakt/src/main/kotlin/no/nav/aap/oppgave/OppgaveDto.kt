package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import no.nav.aap.oppgave.verdityper.OppgaveId
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDateTime
import java.util.UUID

data class OppgaveDto(
    val id: OppgaveId? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: AvklaringsbehovKode,
    val status: Status = Status.OPPRETTET,
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
) {
    init {
        if (journalpostId == null) {
            if (saksnummer == null || behandlingRef == null) {
                throw IllegalArgumentException("Saksnummer og behandlingRef kan ikke være null dersom journalpostId er null")
            }
        }
    }

    fun tilAvklaringsbehovReferanseDto():AvklaringsbehovReferanseDto  {
        return AvklaringsbehovReferanseDto(
            this.saksnummer,
            this.behandlingRef,
            this.journalpostId,
            this.avklaringsbehovKode
        )
    }
}
