package no.nav.aap.oppgave.tildel

import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattFraSøk
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiKlient
import no.nav.aap.oppgave.klienter.nom.ansattinfo.OrgEnhetsType

class TildelOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
){
    private val ansattInfoKlient = NomApiKlient.withClientCredentialsRestClient()

    fun søkEtterSaksbehandlere(søketekst: String, oppgaveId: OppgaveId): List<AnsattFraSøk> {
        val oppgaveTilTildeling = oppgaveRepository.hentOppgave(oppgaveId.id)
        val linje = utledLinjeForOppgave(oppgaveTilTildeling)

        val alleSaksbehandlere = ansattInfoKlient.søkEtterSaksbehandler(søketekst)
        val saksbehandlereForLinje = alleSaksbehandlere.filter { saksbehandler -> saksbehandler.orgTilknytning.map { it.orgEnhetsType }.contains(linje) }

        return saksbehandlereForLinje
    }

    private fun utledLinjeForOppgave(
        oppgave: OppgaveDto
    ): OrgEnhetsType {
        val avklaringsbehovKode = AvklaringsbehovKode(oppgave.avklaringsbehovKode)
        return if (avklaringsbehovKode in
            AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
            + AVKLARINGSBEHOV_FOR_BESLUTTER
            + AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
        ) {
            OrgEnhetsType.NAV_ARBEID_OG_YTELSER
        } else {
            OrgEnhetsType.NAV_KONTOR
        }
    }
}