package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.verdityper.OppgaveId
import tilgang.Operasjon
import tilgang.TilgangRequest

class ReserverOppgaveService(val connection: DBConnection) {

    fun reserverOppgave(avklaringsbehovReferanse: AvklaringsbehovReferanseDto, ident: String, token: OidcToken): List<OppgaveId> {
        val oppgaveRepo = OppgaveRepository(connection)
        val oppgaverSomSkalReserveres = oppgaveRepo.hentÅpneOppgaver(avklaringsbehovReferanse)
        val tilgangRequest = TilgangRequest(
            saksnummer = avklaringsbehovReferanse.saksnummer,
            behandlingsreferanse = avklaringsbehovReferanse.referanse?.toString(),
            avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode.kode,
            operasjon = Operasjon.SAKSBEHANDLE
        )
        if (TilgangGateway.harTilgang(tilgangRequest, token)) {
            val oppgaveRepo = OppgaveRepository(connection)
            oppgaverSomSkalReserveres.forEach {
                oppgaveRepo.reserverOppgave(OppgaveId(it.id), ident, ident)
            }
            return oppgaverSomSkalReserveres
        }
        return listOf()
    }

    fun reserverOppgaveUtenTilgangskontroll(avklaringsbehovReferanse: AvklaringsbehovReferanseDto, ident: String): List<OppgaveId> {
        val oppgaveRepo = OppgaveRepository(connection)
        val oppgaverSomSkalReserveres = oppgaveRepo.hentÅpneOppgaver(avklaringsbehovReferanse)
            oppgaverSomSkalReserveres.forEach {
                oppgaveRepo.reserverOppgave(OppgaveId(it.id), ident, ident)
            }
        return oppgaverSomSkalReserveres
    }

}