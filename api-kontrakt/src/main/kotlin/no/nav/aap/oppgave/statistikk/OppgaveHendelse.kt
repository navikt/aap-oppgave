package no.nav.aap.oppgave.statistikk

import no.nav.aap.oppgave.OppgaveDto

enum class HendelseType {
    OPPRETTET,
    @Deprecated(message = "GJENÅPNET skal ikke brukes lengre. OPPDATERT brukes nå for alle oppdatering av oppgave.", replaceWith = ReplaceWith("OPPDATERT"))
    GJENÅPNET,
    OPPDATERT,
    RESERVERT,
    AVRESERVERT,
    LUKKET
}

data class OppgaveHendelse(
    val hendelse: HendelseType,
    val oppgaveDto: OppgaveDto
)