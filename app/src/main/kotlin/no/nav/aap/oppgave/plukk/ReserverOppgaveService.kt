package no.nav.aap.oppgave.plukk

import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiGateway
import no.nav.aap.oppgave.oppdater.hendelse.KELVIN
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.LoggerFactory
import java.util.UUID


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
        behandlingsReferanse: UUID,
        ident: String
    ) {
        val oppgaveSomSkalReserveres = oppgaveRepository.hentÅpneOppgaver(behandlingsReferanse)
        if (oppgaveSomSkalReserveres == null) {
            log.warn("Fant ingen åpne oppgaver å reservere uten tilgangskontroll gitt behandlingsreferanse $behandlingsReferanse")
            return
        }
        if (ident != KELVIN) {
            oppgaveRepository.reserverOppgave(oppgaveSomSkalReserveres, ident, ident, ansattInfoGateway.hentAnsattNavnHvisFinnes(ident))
            sendOppgaveStatusOppdatering(oppgaveSomSkalReserveres, HendelseType.RESERVERT, flytJobbRepository)
        }
        log.info("Reserverte oppgave ${oppgaveSomSkalReserveres.id} uten tilgangskontroll for $ident.")
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
