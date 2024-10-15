package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.OppgaveId
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.Operasjon

class ReserverOppgaveService(val connection: DBConnection) {

    fun reserverOppgave(
        avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
        ident: String,
        token: OidcToken
    ): List<OppgaveId> {
        val oppgaveRepo = OppgaveRepository(connection)
        val oppgaverSomSkalReserveres = oppgaveRepo.hentÅpneOppgaver(avklaringsbehovReferanse)

        require(avklaringsbehovReferanse.referanse != null || avklaringsbehovReferanse.journalpostId != null) {
            "AvklaringsbehovReferanse må ha referanse til enten behandling eller journalpost"
        }
        val harTilgang = if (avklaringsbehovReferanse.referanse != null)
            TilgangGateway.harTilgangTilBehandling(
                BehandlingTilgangRequest(
                    behandlingsreferanse = avklaringsbehovReferanse.referanse.toString(),
                    avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = Operasjon.SAKSBEHANDLE
                ), token
            )
        else
            TilgangGateway.harTilgangTilJournalpost(
                JournalpostTilgangRequest(
                    journalpostId = avklaringsbehovReferanse.journalpostId!!,
                    avklaringsbehovKode = avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = Operasjon.SAKSBEHANDLE
                ), token
            )


        if (harTilgang) {
            val oppgaveRepo = OppgaveRepository(connection)
            oppgaverSomSkalReserveres.forEach {
                oppgaveRepo.reserverOppgave(it, ident, ident)
            }
            return oppgaverSomSkalReserveres
        }
        return listOf()
    }

    /**
     * Reserver oppgave uten kall mot tilgangkontroll - brukes når oppgave skal reserveres av behandlingsprosess uten
     * uten noen innloggingskontekst.
     */
    fun reserverOppgaveUtenTilgangskontroll(avklaringsbehovReferanse: AvklaringsbehovReferanseDto, ident: String): List<OppgaveId> {
        val oppgaveRepo = OppgaveRepository(connection)
        val oppgaverSomSkalReserveres = oppgaveRepo.hentÅpneOppgaver(avklaringsbehovReferanse)
        oppgaverSomSkalReserveres.forEach {
            oppgaveRepo.reserverOppgave(it, ident, ident)
        }
        return oppgaverSomSkalReserveres
    }

}