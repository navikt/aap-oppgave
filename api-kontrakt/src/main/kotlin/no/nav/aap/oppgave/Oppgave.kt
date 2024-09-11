package no.nav.aap.oppgave

import no.nav.aap.oppgave.opprett.AvklaringsbehovKode
import no.nav.aap.oppgave.opprett.BehandlingRef
import no.nav.aap.oppgave.opprett.Saksnummer
import java.time.LocalDateTime

data class Oppgave(
    val id: OppgaveId? = null,
    val saksnummer: Saksnummer,
    val behandlingRef: BehandlingRef,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: AvklaringsbehovKode,
    val status: Status = Status.OPPRETTET,
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
)
