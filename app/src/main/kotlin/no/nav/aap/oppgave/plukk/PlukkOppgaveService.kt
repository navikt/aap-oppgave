package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.filter.FilterRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgang.BehandlingTilgangRequest
import tilgang.JournalpostTilgangRequest
import tilgang.Operasjon

class PlukkOppgaveService(val connection: DBConnection) {

    private val log: Logger = LoggerFactory.getLogger(PlukkOppgaveService::class.java)

    fun plukkNesteOppgave(filterId: Long, ident: String, token: OidcToken, maksAntallForsøk: Int = 10): NesteOppgaveDto? {
        val filterRepo = FilterRepository(connection)
        val filter = filterRepo.hentFilter(filterId)
        if (filter == null) {
            throw IllegalArgumentException("Finner ikke filter med id: $filterId")
        }
        val oppgaveRepo = OppgaveRepository(connection)
        val nesteOppgaver = oppgaveRepo.finnNesteOppgaver(filter, maksAntallForsøk)
        for ((i, nesteOppgave) in nesteOppgaver.withIndex()) {
            require(nesteOppgave.avklaringsbehovReferanse.referanse != null || nesteOppgave.avklaringsbehovReferanse.journalpostId != null) {
                "AvklaringsbehovReferanse må ha referanse til enten behandling eller journalpost"
            }
            val harTilgang = sjekkTilgang(nesteOppgave, token)
            if (harTilgang) {
                oppgaveRepo.reserverOppgave(OppgaveId(nesteOppgave.oppgaveId, nesteOppgave.oppgaveVersjon), ident, ident)
                log.info("Fant neste oppgave med id ${nesteOppgave.oppgaveId} etter $(i+1) forsøk for filterId $filterId")
                return nesteOppgave
            }
        }
        log.info("Fant ikke neste oppgave etter å ha forsøkt ${nesteOppgaver.size} oppgaver for filterId $filterId")
        return null
    }

    private fun sjekkTilgang (nesteOppgave: NesteOppgaveDto, token: OidcToken): Boolean {
        return if (nesteOppgave.avklaringsbehovReferanse.referanse != null) {
            TilgangGateway.harTilgangTilBehandling(
                BehandlingTilgangRequest(
                    behandlingsreferanse = nesteOppgave.avklaringsbehovReferanse.referanse.toString(),
                    avklaringsbehovKode = nesteOppgave.avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = Operasjon.SAKSBEHANDLE
                ), token
            )
        }
        else {
            TilgangGateway.harTilgangTilJournalpost(
                JournalpostTilgangRequest(
                    journalpostId = nesteOppgave.avklaringsbehovReferanse.journalpostId!!,
                    avklaringsbehovKode = nesteOppgave.avklaringsbehovReferanse.avklaringsbehovKode,
                    operasjon = Operasjon.SAKSBEHANDLE
                ), token
            )
        }
    }

}