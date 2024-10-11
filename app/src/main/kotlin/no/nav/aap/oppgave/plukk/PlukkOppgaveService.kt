package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.filter.FilterRepository
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostRequest
import tilgang.Operasjon

class PlukkOppgaveService(val connection: DBConnection) {

    fun plukkNesteOppgave(filterId: Long, ident: String, token: OidcToken): NesteOppgaveDto? {
        val oppgaveRepo = OppgaveRepository(connection)
        val filterRepo = FilterRepository(connection)
        val filter = filterRepo.hentFilter(filterId)
        if (filter == null) {
            throw IllegalArgumentException("Finner ikke filter med id: $filterId")
        }
        val nesteOppgave = oppgaveRepo.finnNesteOppgave(filter)

        if (nesteOppgave != null) {
            require(nesteOppgave.avklaringsbehovReferanse.referanse != null || nesteOppgave.avklaringsbehovReferanse.journalpostId != null) {
                "AvklaringsbehovReferanse må ha referanse til enten behandling eller journalpost"
            }
            val harTilgang = if (nesteOppgave.avklaringsbehovReferanse.referanse != null)
                TilgangGateway.harTilgangTilBehandling(
                    BehandlingTilgangRequest(
                        behandlingsreferanse = nesteOppgave.avklaringsbehovReferanse.referanse.toString(),
                        avklaringsbehovKode = nesteOppgave.avklaringsbehovReferanse.avklaringsbehovKode,
                        operasjon = Operasjon.SAKSBEHANDLE
                    ), token
                )
            else
                TilgangGateway.harTilgangTilJournalpost(
                    JournalpostRequest(
                        journalpostId = nesteOppgave.avklaringsbehovReferanse.journalpostId!!,
                        avklaringsbehovKode = nesteOppgave.avklaringsbehovReferanse.avklaringsbehovKode,
                        operasjon = Operasjon.SAKSBEHANDLE
                    ), token
                )

            if (harTilgang) {
                oppgaveRepo.reserverOppgave(OppgaveId(nesteOppgave.oppgaveId, nesteOppgave.oppgaveVersjon), ident, ident)
            } else {
                return null
            }
        }

        return nesteOppgave
    }
}