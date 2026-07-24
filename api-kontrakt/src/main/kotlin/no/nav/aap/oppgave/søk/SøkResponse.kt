package no.nav.aap.oppgave.søk

import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling

data class SøkResponse(
    val oppgaver: List<OppgaveISøkResponse>,
    val harTilgang: Boolean,
    val harAdressebeskyttelse: Boolean,
)

data class OppgaveISøkResponse(
    val behandlingskontekst: BehandlingskontekstResponse,
    val avklaringsbehovKode: String,
    val personNavn: String?,
    val reservertAvIdent: String?,
    val erPåVent: Boolean,
    val typeMarkeringer: List<MarkeringForBehandling>,
    val enhetForKø: String
)