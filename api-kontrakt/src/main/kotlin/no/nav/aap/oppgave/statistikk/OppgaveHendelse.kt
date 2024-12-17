package no.nav.aap.oppgave.statistikk

import no.nav.aap.oppgave.OppgaveDto

enum class HendelseType {
    OPPRETTET,
    RESERVERT,
    AVRESERVERT,
    LUKKET
}

data class OppgaveHendelse(
    val hendelse: HendelseType,
    val oppgaveDto: OppgaveDto
)