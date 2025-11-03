package no.nav.aap.oppgave.plukk

import no.nav.aap.motor.FlytJobbRepository
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
        oppgaveId: OppgaveId,
        ident: String
    ): OppgaveId {
        val oppdatertOppgave = oppgaveRepository.hentOppgave(oppgaveId.id)
        if (ident != KELVIN) {
            oppgaveRepository.reserverOppgave(oppdatertOppgave.oppgaveId(), ident, ident, ansattInfoGateway.hentAnsattNavnHvisFinnes(ident))
            val oppdatertOppgave = oppgaveRepository.hentOppgave(oppgaveId.id)
            sendOppgaveStatusOppdatering(oppdatertOppgave.oppgaveId(), HendelseType.RESERVERT, flytJobbRepository)
        }
        log.info("Reserverte oppgave ${oppgaveId.id} uten tilgangskontroll for $ident.")
        return oppgaveId
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
            oppgaveRepository.reserverOppgave(oppgaveId = it.oppgaveId(), endretAvIdent = tildeltAvIdent, reservertAvIdent = tildelTilIdent, reservertAvNavn = ansattInfoGateway.hentAnsattNavnHvisFinnes(tildelTilIdent))
            sendOppgaveStatusOppdatering(it.oppgaveId(), HendelseType.RESERVERT, flytJobbRepository)
            c++
        }
        log.info("Tildelte $c oppgaver til $tildelTilIdent. Saksnumre: ${oppgaverSomSkalReserveres.joinToString(", ") { it.saksnummer.toString() }}")
        return oppgaverSomSkalReserveres.mapNotNull { it.id }
    }
}
