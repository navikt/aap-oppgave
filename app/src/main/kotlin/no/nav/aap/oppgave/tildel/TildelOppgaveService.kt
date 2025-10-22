package no.nav.aap.oppgave.tildel

import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.MsGraphGateway
import org.slf4j.LoggerFactory

class TildelOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val msGraphClient: MsGraphGateway,
){
    private val log = LoggerFactory.getLogger(TildelOppgaveService::class.java)

    fun søkEtterSaksbehandlere(søketekst: String, oppgaver: List<Long>): List<SaksbehandlerDto> {
        val oppgaverTilTildeling = oppgaver.map { oppgave -> oppgaveRepository.hentOppgave(oppgave) }
        val enheter = oppgaverTilTildeling.map { it.enhetForKø }.distinct()

        log.info("Søker på saksbehandlere i enheter $enheter for å tildele oppgaver med id: ${oppgaver.joinToString(", ")}")
        return hentSaksbehandlereMedEnhetstilgang(enheter).filtrerSøkPåNavn(søketekst)
    }

    private fun hentSaksbehandlereMedEnhetstilgang(enheter: List<String>): List<SaksbehandlerDto> {
        val saksbehandlere = enheter.flatMap { msGraphClient.hentMedlemmerIGruppe(it).members }.distinct()
        if (saksbehandlere.isEmpty()) {
            log.warn("Fant ingen saksbehandlere med tilgang til enhet $enheter.")
        }
        return saksbehandlere.map { SaksbehandlerDto(
            navn = it.fornavn + " " + it.etternavn,
            navIdent = it.navIdent,
        ) }
    }

    private fun List<SaksbehandlerDto>.filtrerSøkPåNavn(søketekst: String): List<SaksbehandlerDto> {
        return this.filter { saksbehandler ->
            (saksbehandler.navn?.split(" ")?.any { it.startsWith(søketekst, ignoreCase = true) } == true)
                    || (saksbehandler.navn?.startsWith(søketekst, ignoreCase = true) == true)
                    || (saksbehandler.navIdent.equals(søketekst, ignoreCase = true))
        }
    }
}