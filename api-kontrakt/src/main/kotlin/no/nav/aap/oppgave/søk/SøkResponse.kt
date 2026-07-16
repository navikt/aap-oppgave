package no.nav.aap.oppgave.søk

import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling

data class SøkResponse(
    val oppgaver: List<OppgaveDto>,
    val harTilgang: Boolean,
    val harAdressebeskyttelse: Boolean,
)

data class SøkResponseV2(
    val oppgaver: List<OppgaveISøkResponse>,
    val harTilgang: Boolean,
    val harAdressebeskyttelse: Boolean,
)

data class OppgaveISøkResponse(
    val personNavn: String?,
    val reservertAvIdent: String?,
    val erPåVent: Boolean,
    val typeMarkeringer: List<MarkeringForBehandling>,
    val enhetForKø: String
)