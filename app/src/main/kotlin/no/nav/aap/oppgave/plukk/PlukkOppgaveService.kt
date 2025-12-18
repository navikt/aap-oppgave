package no.nav.aap.oppgave.plukk

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.enhet.NAY_ENHETER
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.klienter.behandlingsflyt.BehandlingsflytGateway
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiGateway
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PlukkOppgaveService(
    val enhetService: IEnhetService,
    val oppgaveRepository: OppgaveRepository,
    val flytJobbRepository: FlytJobbRepository,
    val filterRepository: FilterRepository,
) {
    private val ansattInfoGateway = NomApiGateway.withClientCredentialsRestClient()
    private val log: Logger = LoggerFactory.getLogger(PlukkOppgaveService::class.java)

    fun plukkNesteOppgave(
        filterId: Long,
        enheter: Set<String>,
        ident: String,
        token: OidcToken,
    ): NesteOppgaveDto? {
        val filter = filterRepository.hent(filterId)
            ?: throw IllegalArgumentException("Finner ikke filter med id: $filterId")

        val nesteOppgaver = oppgaveRepository.finnNesteOppgaver(filter.copy(enheter = enheter), MAKS_ANTALL_FORSØK)

        nesteOppgaver.forEachIndexed { i, nesteOppgave ->
            require(
                nesteOppgave.avklaringsbehovReferanse.referanse != null ||
                        nesteOppgave.avklaringsbehovReferanse.journalpostId != null
            ) {
                "AvklaringsbehovReferanse må ha referanse til enten behandling eller journalpost"
            }

            val oppgaveId = OppgaveId(nesteOppgave.oppgaveId, nesteOppgave.oppgaveVersjon)
            val harTilgang = TilgangGateway.sjekkTilgang(nesteOppgave.avklaringsbehovReferanse, token)
            if (harTilgang) {
                oppgaveRepository.reserverOppgave(oppgaveId, ident, ident, ansattInfoGateway.hentAnsattNavnHvisFinnes(ident))
                sendOppgaveStatusOppdatering(oppgaveId, HendelseType.RESERVERT, flytJobbRepository)
                log.info(
                    "Fant neste oppgave med id ${nesteOppgave.oppgaveId} etter ${i + 1} forsøk for filterId $filterId"
                )
                return nesteOppgave
            } else {
                oppdaterOppgaveVedTilgangAvslått(oppgaveId, ident)
            }
        }

        log.warn("Fant ikke neste oppgave etter å ha forsøkt ${nesteOppgaver.size} oppgaver for filter med id ${filterId}, navn: ${filter.navn}, enheter: ${filter.enheter.joinToString(", ")}")
        return null
    }

    fun plukkOppgave(
        oppgaveId: OppgaveId,
        ident: String,
        token: OidcToken
    ): OppgaveDto? {
        val oppgave = oppgaveRepository.hentOppgave(oppgaveId.id)

        val harTilgang = TilgangGateway.sjekkTilgang(oppgave.tilAvklaringsbehovReferanseDto(), token)
        if (harTilgang) {
            if (oppgave.reservertAv == ident) {
                // Reserveres av samme bruker som allerede har reservert oppgave, så da skal ingenting skje.
                return oppgave
            }
            val oppgaveIdMedVersjon = OppgaveId(oppgave.id!!, oppgave.versjon)
            oppgaveRepository.reserverOppgave(
                oppgaveIdMedVersjon,
                ident,
                ident,
                ansattInfoGateway.hentAnsattNavnHvisFinnes(ident)
            )
            sendOppgaveStatusOppdatering(oppgaveIdMedVersjon, HendelseType.RESERVERT, flytJobbRepository)
            return oppgave
        } else {
            log.info("Bruker har ikke tilgang til oppgave med id: $oppgaveId")
            oppdaterOppgaveVedTilgangAvslått(oppgaveId = oppgaveId, ident = ident)
        }
        return null
    }

    private fun oppdaterOppgaveVedTilgangAvslått(oppgaveId: OppgaveId, ident: String) {
        // avreserverer
        avreserverHvisTilgangAvslått(oppgaveId, ident)

        val oppgave = oppgaveRepository.hentOppgave(oppgaveId.id)
        val behandlingRef = requireNotNull(oppgave.behandlingRef) {
            "Oppgave $oppgaveId mangler behandlingsreferanse"
        }
        // Utleder enhet, fortrolig adresse og skjerming på nytt
        val relaterteIdenter = BehandlingsflytGateway.hentRelevanteIdenterPåBehandling(behandlingRef)
        val harFortroligAdresse = enhetService.skalHaFortroligAdresse(oppgave.personIdent, relaterteIdenter)
        val erSkjermet = enhetService.erSkjermet(ident)
        val erOverstyrtTilLokalkontor = oppgave.avklaringsbehovKode in AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER.map { it.kode } && oppgave.enhet !in NAY_ENHETER.map { it.kode }
        val erFørstegangsbehandling = oppgave.behandlingstype == Behandlingstype.FØRSTEGANGSBEHANDLING

        val nyEnhet =
            enhetService.utledEnhetForOppgave(
                AvklaringsbehovKode(oppgave.avklaringsbehovKode),
                oppgave.personIdent,
                relaterteIdenter,
                oppgave.saksnummer,
                erOverstyrtTilLokalkontor,
                erFørstegangsbehandling
            )
        log.info("Oppdaterer enhet for oppgave ${oppgave.id} til ${nyEnhet.gjeldendeEnhet()} etter tilgang avslått på plukk. Saksnummer: ${oppgave.saksnummer}")
        oppgaveRepository.oppdatereOppgave(
            oppgaveId = OppgaveId(requireNotNull(oppgave.id) {
                "OppgaveID kan ikke være null"
            }, oppgave.versjon),
            endretAvIdent = "Kelvin",
            personIdent = oppgave.personIdent,
            enhet = nyEnhet.enhet,
            påVentTil = oppgave.påVentTil,
            påVentÅrsak = oppgave.påVentÅrsak,
            påVentBegrunnelse = oppgave.venteBegrunnelse,
            oppfølgingsenhet = nyEnhet.oppfølgingsenhet,
            veilederArbeid = oppgave.veilederArbeid,
            veilederSykdom = oppgave.veilederSykdom,
            vurderingsbehov = oppgave.vurderingsbehov,
            harFortroligAdresse = harFortroligAdresse,
            erSkjermet = erSkjermet,
            returInformasjon = oppgave.returInformasjon,
            utløptVentefrist = oppgave.utløptVentefrist
        )
        sendOppgaveStatusOppdatering(oppgaveId, HendelseType.OPPDATERT, flytJobbRepository)

    }

    private fun avreserverHvisTilgangAvslått(
        oppgaveId: OppgaveId,
        ident: String,
    ) {
        val oppgave = oppgaveRepository.hentOppgave(oppgaveId.id)
        if (oppgave.reservertAv == ident) {
            log.info("Avreserverer oppgave ${oppgaveId.id} etter at tilgang ble avslått på plukk.")
            oppgaveRepository.avreserverOppgave(oppgaveId, ident)
            sendOppgaveStatusOppdatering(oppgaveId, HendelseType.AVRESERVERT, flytJobbRepository)
        }
    }

    companion object {
        private const val MAKS_ANTALL_FORSØK: Int = 10
    }
}
