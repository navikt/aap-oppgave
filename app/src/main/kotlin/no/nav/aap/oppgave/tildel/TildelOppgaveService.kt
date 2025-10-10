package no.nav.aap.oppgave.tildel

import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.MsGraphClient
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiKlient
import no.nav.aap.oppgave.klienter.nom.ansattinfo.OrgEnhetsType
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import org.slf4j.LoggerFactory

class TildelOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val msGraphClient: MsGraphClient,
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService()
){
    private val ansattInfoKlient = NomApiKlient.withClientCredentialsRestClient()
    private val log = LoggerFactory.getLogger(TildelOppgaveService::class.java)

    fun søkEtterSaksbehandlere(søketekst: String, oppgaver: List<Long>): List<SaksbehandlerDto> {
        val oppgaverTilTildeling = oppgaver.map { oppgave -> oppgaveRepository.hentOppgave(oppgave) }
        val linjer = oppgaverTilTildeling.flatMap { utledLinjeForOppgave(it) }.distinct()

        val enheter = oppgaverTilTildeling.map { it.enhetForKø() }

        log.info("Søker på saksbehandlere i linje $linjer for å tildele oppgaver med id: ${oppgaver.joinToString(", ")}")
        val alleSaksbehandlere = ansattInfoKlient.søkEtterSaksbehandler(søketekst).filter { it.navident != null }
        val saksbehandlereFraNom = alleSaksbehandlere.filter { saksbehandler -> saksbehandler.orgTilknytning?.mapNotNull { it.orgEnhet?.orgEnhetsType }?.any {it in linjer} == true }
        val saksbehandlereMedTilgang = hentSaksbehandlereMedEnhetstilgang(enheter).filtrerSøkPåNavn(søketekst)

        return if (unleashService.isEnabled(FeatureToggles.HentSaksbehandlereForEnhet)) {
                saksbehandlereMedTilgang
            } else {
                saksbehandlereFraNom.map { SaksbehandlerDto(
                    navn = it.visningsnavn,
                    navIdent = requireNotNull(it.navident) { "Saksbehandler fra søk mangler navIdent" },
                ) }
            }
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
            saksbehandler.navn?.split(" ")?.any {
                it.startsWith(søketekst, ignoreCase = true)
            } == true
        }
    }

    private fun utledLinjeForOppgave(
        oppgave: OppgaveDto
    ): List<OrgEnhetsType> {
        val avklaringsbehovKode = AvklaringsbehovKode(oppgave.avklaringsbehovKode)
        return if (avklaringsbehovKode in
            AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
            + AVKLARINGSBEHOV_FOR_BESLUTTER
            + AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
        ) {
            listOf(OrgEnhetsType.NAV_ARBEID_OG_YTELSER)
        } else {
            listOf(OrgEnhetsType.NAV_KONTOR, OrgEnhetsType.ARBEIDSLIVSSENTER)
        }
    }
}