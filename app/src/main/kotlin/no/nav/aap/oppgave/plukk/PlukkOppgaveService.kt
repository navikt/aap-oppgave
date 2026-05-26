package no.nav.aap.oppgave.plukk

import javax.sql.DataSource
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.enhet.NAY_ENHETER
import no.nav.aap.oppgave.klienter.behandlingsflyt.BehandlingsflytGateway
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoGateway
import no.nav.aap.oppgave.oppdater.hendelse.KELVIN
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PlukkOppgaveService(
    val enhetService: IEnhetService,
    val oppgaveRepository: OppgaveRepository,
    val flytJobbRepository: FlytJobbRepository,
    val reserverOppgaveService: ReserverOppgaveService,
) {
    constructor(enhetService: IEnhetService, ansattInfoGateway: AnsattInfoGateway, connection: DBConnection) : this(
        enhetService = enhetService,
        oppgaveRepository = OppgaveRepository(connection),
        flytJobbRepository = FlytJobbRepositoryImpl(connection),
        reserverOppgaveService = ReserverOppgaveService(connection, ansattInfoGateway)
    )

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private fun oppdaterOppgaveVedTilgangAvslått(oppgaveId: OppgaveId, ident: String) {
        log.info("Bruker har ikke tilgang til oppgave med id: $oppgaveId")
        // avreserverer
        avreserverHvisTilgangAvslått(oppgaveId, ident)

        val oppgave = oppgaveRepository.hentOppgave(oppgaveId.id)
        val behandlingRef = oppgave.behandlingRef
        // Utleder enhet, fortrolig adresse og skjerming på nytt
        val relevanteIdenter = if (oppgave.behandlingstype.fraBehandlingsflyt) {
            BehandlingsflytGateway.hentRelevanteIdenterPåBehandling(behandlingRef)
        } else {
            // skal ikke prøve å hente identer fra behandlingsflyt for postmottak-behandling
            emptyList()
        }
        val harFortroligAdresse = enhetService.skalHaFortroligAdresse(oppgave.personIdent, relevanteIdenter)
        val erSkjermet = enhetService.erSkjermet(ident)
        val erOverstyrtTilLokalkontor = oppgave.avklaringsbehovKode in AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER.map { it.kode } && oppgave.enhet !in NAY_ENHETER.map { it.kode }
        val erFørstegangsbehandling = oppgave.behandlingstype == Behandlingstype.FØRSTEGANGSBEHANDLING

        val nyEnhet =
            enhetService.utledEnhetForOppgave(
                AvklaringsbehovKode(oppgave.avklaringsbehovKode),
                oppgave.personIdent,
                relevanteIdenter,
                oppgave.saksnummer,
                erOverstyrtTilLokalkontor,
                erFørstegangsbehandling
            )
        log.info("Oppdaterer enhet for oppgave ${oppgave.id} til ${nyEnhet.gjeldendeEnhet()} etter tilgang avslått på plukk. Saksnummer: ${oppgave.saksnummer}")
        oppgaveRepository.oppdatereOppgave(
            oppgaveId = oppgave.oppgaveId(),
            endretAvIdent = KELVIN,
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
            reserverOppgaveService.avreserverOppgave(oppgaveId, ident)
        }
    }

    companion object {
        suspend fun plukkOppgave(
            dataSource: DataSource,
            enhetService: IEnhetService,
            ansattInfoGateway: AnsattInfoGateway,
            token: OidcToken,
            ident: String,
            oppgaveId: Long,
        ): OppgaveDto? {
            val oppgave = dataSource.transaction(readOnly = true) {
                OppgaveRepository(it).hentOppgave(oppgaveId)
            }

            val harTilgang = TilgangService.sjekkTilgang(oppgave.tilAvklaringsbehovReferanseDto(), token)

            if (!harTilgang) {
                dataSource.transaction {
                    PlukkOppgaveService(enhetService, ansattInfoGateway, it)
                        .oppdaterOppgaveVedTilgangAvslått(oppgaveId = oppgave.oppgaveId(), ident = ident)
                }
                return null
            }

            if (oppgave.reservertAv != ident) {
                dataSource.transaction { connection ->
                    ReserverOppgaveService(connection, ansattInfoGateway).reserverOppgave(oppgave.oppgaveId(), ident, ident)
                }
            }

            return oppgave
        }
    }
}
