package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.prosessering.StatistikkHendelseJobb
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.statistikk.HendelseType

class ReserverOppgaveService(val connection: DBConnection) {

    private val oppgaveRepository = OppgaveRepository(connection)

    fun reserverOppgave(
        avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
        ident: String,
        token: OidcToken
    ): List<OppgaveId> {
        val oppgaverSomSkalReserveres = oppgaveRepository.hentÅpneOppgaver(avklaringsbehovReferanse)

        require(avklaringsbehovReferanse.referanse != null || avklaringsbehovReferanse.journalpostId != null) {
            "AvklaringsbehovReferanse må ha referanse til enten behandling eller journalpost"
        }

        val harTilgang = TilgangGateway.sjekkTilgang(avklaringsbehovReferanse, token)
        if (harTilgang) {
            oppgaverSomSkalReserveres.forEach {
                oppgaveRepository.reserverOppgave(it, ident, ident)
                sendOppgaveStatusOppdatering(connection, it, HendelseType.RESERVERT)
            }
            return oppgaverSomSkalReserveres
        }
        return listOf()
    }

    fun avreserverOppgave(
        oppgaveId: OppgaveId,
        ident: String,
    ) {
        oppgaveRepository.avreserverOppgave(oppgaveId, ident)
        sendOppgaveStatusOppdatering(connection, oppgaveId, HendelseType.AVRESERVERT)
    }

    /**
     * Reserver oppgave uten kall mot tilgangkontroll - brukes når oppgave skal reserveres av behandlingsprosess uten
     * uten noen innloggingskontekst.
     */
    fun reserverOppgaveUtenTilgangskontroll(avklaringsbehovReferanse: AvklaringsbehovReferanseDto, ident: String): List<OppgaveId> {
        val oppgaverSomSkalReserveres = oppgaveRepository.hentÅpneOppgaver(avklaringsbehovReferanse)
        oppgaverSomSkalReserveres.forEach {
            oppgaveRepository.reserverOppgave(it, ident, ident)
            sendOppgaveStatusOppdatering(connection, it, HendelseType.RESERVERT)
        }
        return oppgaverSomSkalReserveres
    }

}