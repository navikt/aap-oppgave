package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetForOppgave
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PlukkOppgaveService(val connection: DBConnection, val enhetService: EnhetService) {

    private val log: Logger = LoggerFactory.getLogger(PlukkOppgaveService::class.java)

    fun plukkNesteOppgave(
        filterId: Long,
        enheter: Set<String>,
        ident: String,
        token: OidcToken,
        maksAntallForsøk: Int = 10
    ): NesteOppgaveDto? {
        val filterRepo = FilterRepository(connection)
        val filter = filterRepo.hent(filterId)
        if (filter == null) {
            throw IllegalArgumentException("Finner ikke filter med id: $filterId")
        }
        val oppgaveRepo = OppgaveRepository(connection)
        val nesteOppgaver = oppgaveRepo.finnNesteOppgaver(filter.copy(enheter = enheter), maksAntallForsøk)
        for ((i, nesteOppgave) in nesteOppgaver.withIndex()) {
            require(nesteOppgave.avklaringsbehovReferanse.referanse != null || nesteOppgave.avklaringsbehovReferanse.journalpostId != null) {
                "AvklaringsbehovReferanse må ha referanse til enten behandling eller journalpost"
            }

            val harTilgang = TilgangGateway.sjekkTilgang(nesteOppgave.avklaringsbehovReferanse, token)
            if (harTilgang) {
                val oppgaveId = OppgaveId(nesteOppgave.oppgaveId, nesteOppgave.oppgaveVersjon)
                oppgaveRepo.reserverOppgave(oppgaveId, ident, ident)
                sendOppgaveStatusOppdatering(oppgaveId, HendelseType.RESERVERT, FlytJobbRepository(connection))
                log.info("Fant neste oppgave med id ${nesteOppgave.oppgaveId} etter ${i + 1} forsøk for filterId $filterId")
                return nesteOppgave
            }
        }
        log.info("Fant ikke neste oppgave etter å ha forsøkt ${nesteOppgaver.size} oppgaver for filterId $filterId")
        return null
    }

    fun plukkOppgave(oppgaveId: OppgaveId, ident: String, token: OidcToken): OppgaveDto? {
        val oppgaveRepo = OppgaveRepository(connection)
        val oppgave = oppgaveRepo.hentOppgave(oppgaveId)

        val harTilgang = TilgangGateway.sjekkTilgang(oppgave.tilAvklaringsbehovReferanseDto(), token)
        if (harTilgang) {
            if (oppgave.reservertAv == ident) {
                // Reserveres av samme bruker som allerede har reservert oppgave, så da skal ingenting skje.
                return oppgave
            }
            val oppgaveIdMedVersjon = OppgaveId(oppgave.id!!, oppgave.versjon)
            oppgaveRepo.reserverOppgave(oppgaveIdMedVersjon, ident, ident)
            sendOppgaveStatusOppdatering(oppgaveIdMedVersjon, HendelseType.RESERVERT, FlytJobbRepository(connection))
            return oppgave
        }

        // Sjekk om enhet må oppdateres dersom tilgang blir avslått
        val nyEnhet =
            enhetService.utledEnhetForOppgave(AvklaringsbehovKode(oppgave.avklaringsbehovKode), oppgave.personIdent)
        if (nyEnhet != EnhetForOppgave(oppgave.enhet, oppgave.oppfølgingsenhet)) {
            oppdaterUtdatertEnhet(oppgave, oppgaveRepo, nyEnhet)
        }
        return null
    }

    private fun oppdaterUtdatertEnhet(oppgave: OppgaveDto, oppgaveRepo: OppgaveRepository, nyEnhet: EnhetForOppgave) {
        log.info("Oppdaterer enhet for oppgave ${oppgave.id} etter at tilgang ble avslått på plukk.")
        oppgaveRepo.oppdatereOppgave(
            oppgaveId = OppgaveId(oppgave.id!!, oppgave.versjon),
            ident = "Kelvin",
            personIdent = oppgave.personIdent,
            enhet = nyEnhet.enhet,
            påVentTil = oppgave.påVentTil,
            påVentÅrsak = oppgave.påVentÅrsak,
            oppfølgingsenhet = nyEnhet.oppfølgingsenhet,
            veileder = oppgave.veileder,
            årsakerTilBehandling = oppgave.årsakerTilBehandling
        )
    }
}

