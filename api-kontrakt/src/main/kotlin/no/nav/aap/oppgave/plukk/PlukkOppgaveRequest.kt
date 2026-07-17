package no.nav.aap.oppgave.plukk

data class PlukkOppgaveRequest(val oppgaveId: Long, val versjon: Long)

data class PlukkOppgaveDto(val oppgaveId: Long, val versjon: Long)