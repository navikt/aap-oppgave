package no.nav.aap.oppgave.tildel

import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.MsGraphClient
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattFraSøk
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

    fun søkEtterSaksbehandlere(søketekst: String, oppgaver: List<Long>): List<AnsattFraSøk> {
        val oppgaverTilTildeling = oppgaver.map { oppgave -> oppgaveRepository.hentOppgave(oppgave) }
        val linjer = oppgaverTilTildeling.flatMap { utledLinjeForOppgave(it) }.distinct()

        val enheter = oppgaverTilTildeling.map { it.enhetForKø() }

        log.info("Søker på saksbehandlere i linje $linjer for å tildele oppgaver med id: ${oppgaver.joinToString(", ")}")
        val alleSaksbehandlere = ansattInfoKlient.søkEtterSaksbehandler(søketekst).filter { it.navident != null }
        val saksbehandlereForLinje = alleSaksbehandlere.filter { saksbehandler -> saksbehandler.orgTilknytning?.mapNotNull { it.orgEnhet?.orgEnhetsType }?.any {it in linjer} == true }

        return if (unleashService.isEnabled(FeatureToggles.HentSaksbehandlereForEnhet)) {
                filtrerSaksbehandlerePåGruppemedlemskap(enheter, saksbehandlereForLinje)
            } else {
                saksbehandlereForLinje
            }
    }

    private fun filtrerSaksbehandlerePåGruppemedlemskap(enheter: List<String>, saksbehandlere: List<AnsattFraSøk>): List<AnsattFraSøk> {
        val relevantGruppeMedlemmer = enheter.flatMap { msGraphClient.hentMedlemmerIGruppe(it).members }
        val overlapp = saksbehandlere.filter { saksbehandler -> saksbehandler.navident in relevantGruppeMedlemmer.map { it.navIdent }}

        return overlapp.ifEmpty {
            log.warn("Fant ingen saksbehandlere med enhetstilgang gitt søketeksten. Enhet: $enheter")
            saksbehandlere
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