package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.filter.FilterRepository
import tilgang.Operasjon
import tilgang.TilgangRequest

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
            val tilgangRequest = TilgangRequest(
                saksnummer = nesteOppgave.saksnummer!!,
                behandlingsreferanse = nesteOppgave.behandlingRef!!.toString(),
                avklaringsbehovKode = nesteOppgave.avklaringsbehovKode,
                operasjon = Operasjon.SAKSBEHANDLE
            )
            if (TilgangGateway.harTilgang(tilgangRequest, token)) {

                oppgaveRepo.reserverOppgave(nesteOppgave.oppgaveId, ident)
            }
        }
        return nesteOppgave
    }

}