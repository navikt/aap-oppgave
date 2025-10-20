package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiGateway
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.LoggerFactory

private const val KELVIN = "Kelvin"

class ReserverOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository,
) {
    private val ansattInfoGateway = NomApiGateway.withClientCredentialsRestClient()
    private val log = LoggerFactory.getLogger(javaClass)

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
                oppgaveRepository.reserverOppgave(it, ident, ident, ansattInfoGateway.hentAnsattNavnHvisFinnes(ident))
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
        var c = 0
        oppgaverSomSkalReserveres.forEach {
            if (ident != KELVIN) {
                oppgaveRepository.reserverOppgave(it, ident, ident, ansattInfoGateway.hentAnsattNavnHvisFinnes(ident))
                sendOppgaveStatusOppdatering(it, HendelseType.RESERVERT, flytJobbRepository)
                c++
            }
        }
        log.info("Reserverte $c oppgaver uten tilgangskontroll for $ident.")
        return oppgaverSomSkalReserveres
    }

    fun tildelOppgaver(
        oppgaver: List<Long>,
        tildelTilIdent: String,
        tildeltAvIdent: String,
    ): List<Long> {
        // Tildeler uten tilgangskontroll inntil videre
        val oppgaverSomSkalReserveres = oppgaver.map { oppgaveRepository.hentOppgave(it) }
        var c = 0
        oppgaverSomSkalReserveres.forEach {
            oppgaveRepository.reserverOppgave(oppgaveId = OppgaveId(it.id!!, it.versjon), endretAvIdent = tildeltAvIdent, reservertAvIdent = tildelTilIdent, reservertAvNavn = ansattInfoGateway.hentAnsattNavnHvisFinnes(tildelTilIdent))
            sendOppgaveStatusOppdatering(OppgaveId(it.id!!, it.versjon), HendelseType.RESERVERT, flytJobbRepository)
            c++
        }
        log.info("Tildelte $c oppgaver til $tildelTilIdent. Saksnumre: ${oppgaverSomSkalReserveres.joinToString(", ") { it.saksnummer.toString() }}")
        return oppgaverSomSkalReserveres.mapNotNull { it.id }
    }


}
