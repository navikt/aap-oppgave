package no.nav.aap.oppgave.historikk

import java.time.LocalDateTime
import no.nav.aap.oppgave.verdityper.Status

data class OppgaveHistorikk(
    val id: Long,
    val oppgaveId: Long,
    val status: Status,
    val reservertAv: String?,
    val reservertTidspunkt: LocalDateTime?,
    val endretAv: String?,
    val endretTidspunkt: LocalDateTime?,
    val enhet: String,
    val oppfølgingsenhet: String?,
)
