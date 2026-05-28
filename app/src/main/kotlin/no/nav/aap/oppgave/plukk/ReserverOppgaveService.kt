package no.nav.aap.oppgave.plukk

import java.util.*
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoGateway
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.LoggerFactory


class ReserverOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository,
    private val ansattInfoGateway: AnsattInfoGateway,
) {
    constructor(connection: DBConnection, ansattInfoGateway: AnsattInfoGateway): this(
        oppgaveRepository = OppgaveRepository(connection),
        flytJobbRepository = FlytJobbRepository(connection),
        ansattInfoGateway = ansattInfoGateway,
    )

    private val log = LoggerFactory.getLogger(this::class.java)

    fun reserverOppgave(oppgaveId: OppgaveId, endretAvIdent: String, reservertAvIdent: String) {
        oppgaveRepository.reserverOppgave(
            oppgaveId,
            endretAvIdent = endretAvIdent,
            reservertAvIdent = reservertAvIdent,
            reservertAvNavn = ansattInfoGateway.hentAnsattNavnHvisFinnes(reservertAvIdent),
        )
        sendOppgaveStatusOppdatering(oppgaveId, HendelseType.RESERVERT, flytJobbRepository)
    }

    fun avreserverOppgave(
        oppgaveId: OppgaveId,
        ident: String,
    ) {
        log.info("Avreserverer oppgave $oppgaveId fra $ident")
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
        reserverOppgave(oppgaveSomSkalReserveres, ident, ident)
        log.info("Reserverte oppgave $oppgaveSomSkalReserveres uten tilgangskontroll for $ident.")
    }

    fun tildelOppgaver(
        oppgaver: List<Long>,
        tildelTilIdent: String,
        tildeltAvIdent: String,
    ): List<Long> {
        // Tildeler uten tilgangskontroll inntil videre
        val oppgaverSomSkalReserveres = oppgaver.map { oppgaveRepository.hentOppgave(it) }
        oppgaverSomSkalReserveres.forEach {
            reserverOppgave(it.oppgaveId(), endretAvIdent = tildeltAvIdent, reservertAvIdent = tildelTilIdent)
        }

        log.info("Tildelte ${oppgaverSomSkalReserveres.size} oppgaver til $tildelTilIdent. Saksnumre: ${oppgaverSomSkalReserveres.joinToString(", ") { it.saksnummer.toString() }}")
        return oppgaverSomSkalReserveres.mapNotNull { it.id }
    }
}
