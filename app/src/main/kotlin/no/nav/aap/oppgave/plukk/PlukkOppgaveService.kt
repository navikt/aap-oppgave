package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.OppgaveId
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
                saksnummer = nesteOppgave.avklaringsbehovReferanse.saksnummer!!,
                behandlingsreferanse = nesteOppgave.avklaringsbehovReferanse.referanse?.toString(),
                avklaringsbehovKode = nesteOppgave.avklaringsbehovReferanse.avklaringsbehovKode,
                operasjon = Operasjon.SAKSBEHANDLE
            )
            if (TilgangGateway.harTilgang(tilgangRequest, token)) {

                oppgaveRepo.reserverOppgave(OppgaveId(nesteOppgave.oppgaveId), ident, ident)
            }
        }
        return nesteOppgave
    }

}