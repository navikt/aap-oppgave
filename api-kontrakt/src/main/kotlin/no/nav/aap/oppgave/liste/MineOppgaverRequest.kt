package no.nav.aap.oppgave.liste

data class MineOppgaverRequest(
    val kunPaaVent: Boolean? = false,
    val sortby: OppgaveSorteringFelt? = null,
    val sortorder: OppgaveSorteringRekkefølge? = null,
)