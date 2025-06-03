package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetForOppgave
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PlukkOppgaveService(
    val enhetService: IEnhetService,
    val oppgaveRepository: OppgaveRepository,
    val flytJobbRepository: FlytJobbRepository,
    val filterRepository: FilterRepository
) {
    private val log: Logger = LoggerFactory.getLogger(PlukkOppgaveService::class.java)

    fun plukkNesteOppgave(
        filterId: Long,
        enheter: Set<String>,
        ident: String,
        token: OidcToken,
        maksAntallForsøk: Int = 10
    ): NesteOppgaveDto? {
        val filter = filterRepository.hent(filterId)
        if (filter == null) {
            throw IllegalArgumentException("Finner ikke filter med id: $filterId")
        }
        val oppgaveRepo = oppgaveRepository
        val nesteOppgaver = oppgaveRepo.finnNesteOppgaver(filter.copy(enheter = enheter), maksAntallForsøk)
        for ((i, nesteOppgave) in nesteOppgaver.withIndex()) {
            require(
                nesteOppgave.avklaringsbehovReferanse.referanse != null ||
                        nesteOppgave.avklaringsbehovReferanse.journalpostId != null
            ) {
                "AvklaringsbehovReferanse må ha referanse til enten behandling eller journalpost"
            }

            val oppgaveId = OppgaveId(nesteOppgave.oppgaveId, nesteOppgave.oppgaveVersjon)
            val harTilgang = TilgangGateway.sjekkTilgang(nesteOppgave.avklaringsbehovReferanse, token)
            if (harTilgang) {
                oppgaveRepo.reserverOppgave(oppgaveId, ident, ident)
                sendOppgaveStatusOppdatering(oppgaveId, HendelseType.RESERVERT, flytJobbRepository)
                log.info(
                    "Fant neste oppgave med id ${nesteOppgave.oppgaveId} etter ${i + 1} forsøk for filterId $filterId"
                )
                return nesteOppgave
            } else {
                avreserverHvisTilgangAvslått(oppgaveId = oppgaveId, ident = ident, oppgaveRepo = oppgaveRepo)
                sjekkFortroligAdresse(oppgaveId = oppgaveId, oppgaveRepo = oppgaveRepo)
                oppdaterUtdatertEnhet(oppgaveId = oppgaveId, oppgaveRepo = oppgaveRepo)
            }
        }
        log.info("Fant ikke neste oppgave etter å ha forsøkt ${nesteOppgaver.size} oppgaver for filterId $filterId")
        return null
    }

    fun plukkOppgave(
        oppgaveId: OppgaveId,
        ident: String,
        token: OidcToken
    ): OppgaveDto? {
        val oppgave = oppgaveRepository.hentOppgave(oppgaveId)

        val harTilgang = TilgangGateway.sjekkTilgang(oppgave.tilAvklaringsbehovReferanseDto(), token)
        if (harTilgang) {
            if (oppgave.reservertAv == ident) {
                // Reserveres av samme bruker som allerede har reservert oppgave, så da skal ingenting skje.
                return oppgave
            }
            val oppgaveIdMedVersjon = OppgaveId(oppgave.id!!, oppgave.versjon)
            oppgaveRepository.reserverOppgave(oppgaveIdMedVersjon, ident, ident)
            sendOppgaveStatusOppdatering(oppgaveIdMedVersjon, HendelseType.RESERVERT, flytJobbRepository)
            return oppgave
        } else {
            avreserverHvisTilgangAvslått(oppgaveId = oppgaveId, ident = ident, oppgaveRepo = oppgaveRepository)
            sjekkFortroligAdresse(oppgaveId = oppgaveId, oppgaveRepo = oppgaveRepository)
            oppdaterUtdatertEnhet(oppgaveId = oppgaveId, oppgaveRepo = oppgaveRepository)
        }
        return null
    }

    private fun avreserverHvisTilgangAvslått(
        oppgaveId: OppgaveId,
        ident: String,
        oppgaveRepo: OppgaveRepository
    ) {
        val oppgave = oppgaveRepo.hentOppgave(oppgaveId)
        if (oppgave.reservertAv == ident) {
            log.info("Avreserverer oppgave ${oppgaveId.id} etter at tilgang ble avslått på plukk.")
            oppgaveRepo.avreserverOppgave(oppgaveId, ident)
            sendOppgaveStatusOppdatering(oppgaveId, HendelseType.AVRESERVERT, flytJobbRepository)
        }
    }

    private fun sjekkFortroligAdresse(
        oppgaveId: OppgaveId,
        oppgaveRepo: OppgaveRepository
    ) {
        val oppgave = oppgaveRepo.hentOppgave(oppgaveId)
        val harFortroligAdresse = enhetService.harFortroligAdresse(oppgave.personIdent)
        if (harFortroligAdresse != (oppgave.harFortroligAdresse == true)) {
            oppgaveRepo.settFortroligAdresse(OppgaveId(oppgave.id!!, oppgave.versjon), harFortroligAdresse)
        }
    }

    private fun oppdaterUtdatertEnhet(
        oppgaveId: OppgaveId,
        oppgaveRepo: OppgaveRepository
    ) {
        val oppgave = oppgaveRepo.hentOppgave(oppgaveId)
        val oppgaveId = OppgaveId(oppgave.id!!, oppgave.versjon)

        val nyEnhet =
            enhetService.utledEnhetForOppgave(
                AvklaringsbehovKode(oppgave.avklaringsbehovKode),
                oppgave.personIdent
            )

        if (nyEnhet != EnhetForOppgave(oppgave.enhet, oppgave.oppfølgingsenhet)) {
            log.info("Oppdaterer enhet for oppgave $oppgaveId etter at tilgang ble avslått på plukk.")
            oppgaveRepo.oppdatereOppgave(
                oppgaveId = oppgaveId,
                ident = "Kelvin",
                personIdent = oppgave.personIdent,
                enhet = nyEnhet.enhet,
                påVentTil = oppgave.påVentTil,
                påVentÅrsak = oppgave.påVentÅrsak,
                påVentBegrunnelse = oppgave.venteBegrunnelse,
                oppfølgingsenhet = nyEnhet.oppfølgingsenhet,
                veilederArbeid = oppgave.veilederArbeid,
                veilederSykdom = oppgave.veilederSykdom,
                årsakerTilBehandling = oppgave.årsakerTilBehandling,
                harFortroligAdresse = oppgave.harFortroligAdresse,
                returInformasjon = oppgave.returInformasjon
            )
            sendOppgaveStatusOppdatering(oppgaveId, HendelseType.OPPDATERT, flytJobbRepository)
        }
    }
}