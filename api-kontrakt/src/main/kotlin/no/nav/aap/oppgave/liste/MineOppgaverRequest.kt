package no.nav.aap.oppgave.liste

import com.papsign.ktor.openapigen.annotations.parameters.QueryParam

data class MineOppgaverRequest(
    @param:QueryParam("Vis kun på vent-oppgaver.") val kunPaaVent: Boolean? = false,
    @param:QueryParam("Sorter oppgaveliste") val sortby: OppgaveSorteringFelt? = null,
    @param:QueryParam("Sorteringsrekkefølge") val sortorder: OppgaveSorteringRekkefølge? = null,
)