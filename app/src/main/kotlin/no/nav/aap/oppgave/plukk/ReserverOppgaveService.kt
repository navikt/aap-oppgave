package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType

class ReserverOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository
) {

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
                sendOppgaveStatusOppdatering(it, HendelseType.RESERVERT, flytJobbRepository)
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
        sendOppgaveStatusOppdatering(oppgaveId, HendelseType.AVRESERVERT, flytJobbRepository)
    }

    /**
     * Reserver oppgave uten kall mot tilgangkontroll - brukes når oppgave skal reserveres av behandlingsprosess uten
     * uten noen innloggingskontekst.
     */
    fun reserverOppgaveUtenTilgangskontroll(
        avklaringsbehovReferanse: AvklaringsbehovReferanseDto,
        ident: String
    ): List<OppgaveId> {
        val oppgaverSomSkalReserveres = oppgaveRepository.hentÅpneOppgaver(avklaringsbehovReferanse)
        oppgaverSomSkalReserveres.forEach {
            oppgaveRepository.reserverOppgave(it, ident, ident)
            sendOppgaveStatusOppdatering(it, HendelseType.RESERVERT, flytJobbRepository)
        }
        return oppgaverSomSkalReserveres
    }

}
