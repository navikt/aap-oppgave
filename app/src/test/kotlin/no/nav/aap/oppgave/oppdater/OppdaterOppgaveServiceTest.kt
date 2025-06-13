package no.nav.aap.oppgave.oppdater

import io.getunleash.FakeUnleash
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilRetur
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.EnhetForOppgave
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.klienter.msgraph.Group
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.msgraph.MemberOf
import no.nav.aap.oppgave.klienter.oppfolging.ISykefravarsoppfolgingKlient
import no.nav.aap.oppgave.klienter.oppfolging.IVeilarbarboppfolgingKlient
import no.nav.aap.oppgave.unleash.UnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.postmottak.kontrakt.journalpost.JournalpostId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.AfterTest
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as BehandlingStatus

class OppdaterOppgaveServiceTest {

    private val dataSource = InitTestDatabase.freshDatabase()

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            // Unleash
            UnleashServiceProvider.setUnleashService(
                UnleashService(FakeUnleash().apply {
                    enableAll()
                })
            )
        }
    }

    @AfterTest
    fun tearDown() {
        @Suppress("SqlWithoutWhere")
        dataSource.transaction {
            it.execute("DELETE FROM OPPGAVE_HISTORIKK")
            it.execute("DELETE FROM OPPGAVE")
        }
    }

    @Test
    fun `Ved flere åpne avklaringsbehov skal det opprettes oppgave på behovet som historisk først ble opprettet`() {
        val (sykdomOppgaveId, saksnummer, behandlingsref) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name)
        )
        val (fastsettBeregningstidspunktOppgaveId) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT.kode.name)
        )

        val nå = LocalDateTime.now()

        val hendelse = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(10)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(9)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå.minusHours(6),
                            begrunnelse = "Fordi det er en feil",
                            årsakTilRetur = listOf(
                                ÅrsakTilRetur(
                                    ÅrsakTilReturKode.MANGLENDE_UTREDNING
                                )
                            )
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(6)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(5)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå.minusHours(4),
                            begrunnelse = "Fordi det er en feil",
                            årsakTilRetur = listOf(
                                ÅrsakTilRetur(
                                    ÅrsakTilReturKode.MANGLENDE_UTREDNING
                                )
                            )
                        )
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT,
                    status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(8)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(7)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå.minusHours(4),
                            begrunnelse = "Fordi det er en feil",
                            årsakTilRetur = listOf(
                                ÅrsakTilRetur(
                                    ÅrsakTilReturKode.MANGLENDE_UTREDNING
                                )
                            )
                        )
                    )
                )
            )
        )
        sendBehandlingFlytStoppetHendelse(hendelse)

        val sykdomOppgave = hentOppgave(sykdomOppgaveId)
        assertThat(sykdomOppgave.status).isEqualTo(Status.OPPRETTET)
        val fastsettBeregningstidspunktOppgave = hentOppgave(fastsettBeregningstidspunktOppgaveId)
        assertThat(fastsettBeregningstidspunktOppgave.status).isEqualTo(Status.AVSLUTTET)
    }

    @Test
    fun `oppgaver fra postmottak lukkes etter at saksnummer er satt`() {
        val behandlingsref = UUID.randomUUID().let(::BehandlingReferanse)
        val saksnummer = "123".let(::Saksnummer)

        val nå = LocalDateTime.now()

        val hendelse = DokumentflytStoppetHendelse(
            journalpostId = JournalpostId(123),
            ident = "personIdent",
            referanse = behandlingsref.referanse,
            behandlingType = no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling.Journalføring,
            status = no.nav.aap.postmottak.kontrakt.behandling.Status.OPPRETTET,
            avklaringsbehov = listOf(
                no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon.AVKLAR_SAK,
                    status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                    endringer = listOf(
                        no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO(
                            status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(1)
                        ),
                    )
                )
            ),
            opprettetTidspunkt = nå.minusHours(1),
            saksnummer = null,
            hendelsesTidspunkt = nå.minusHours(1),
        )
        sendDokumentFlytStoppetHendelse(hendelse)

        val oppgaverPåBehandling = hentOppgaverForBehandling(behandlingsref = behandlingsref)
        assertThat(oppgaverPåBehandling).hasSize(1)
        assertThat(oppgaverPåBehandling.first().status).isEqualTo(Status.OPPRETTET)
        assertThat(oppgaverPåBehandling.first().avklaringsbehovKode).isEqualTo("1340")

        val hendelse2 = DokumentflytStoppetHendelse(
            journalpostId = JournalpostId(123),
            ident = "personIdent",
            referanse = behandlingsref.referanse,
            behandlingType = no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling.Journalføring,
            status = no.nav.aap.postmottak.kontrakt.behandling.Status.AVSLUTTET,
            avklaringsbehov = listOf(
                no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon.AVKLAR_SAK,
                    status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                    endringer = listOf(
                        no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO(
                            status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå
                        ),
                    )
                )
            ),
            opprettetTidspunkt = nå,
            saksnummer = saksnummer.toString(),
            hendelsesTidspunkt = nå,
        )
        sendDokumentFlytStoppetHendelse(hendelse2)

        val oppgaverPåBehandling2 = hentOppgaverForBehandling(behandlingsref)
        assertThat(oppgaverPåBehandling2).hasSize(1)
        assertThat(oppgaverPåBehandling2.first().status).isEqualTo(Status.AVSLUTTET)
        assertThat(oppgaverPåBehandling2.first().avklaringsbehovKode).isEqualTo("1340")


    }

    @Test
    fun `ved ventebehov skal åpne oppgaver markeres med venteårsaker`() {
        val behandlingsref = UUID.randomUUID().let(::BehandlingReferanse)
        val saksnummer = "123".let(::Saksnummer)

        val nå = LocalDateTime.now()

        val hendelse = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(10)
                        ),
                    )
                ),
            )
        )

        sendBehandlingFlytStoppetHendelse(hendelse)

        val åpneOppgaver = hentOppgaverForBehandling(behandlingsref)
        assertThat(åpneOppgaver).hasSize(1)

        val venteFrist = LocalDate.now().plusDays(1)

        val hendelse2 = hendelse.copy(
            erPåVent = true, avklaringsbehov = hendelse.avklaringsbehov + AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = Definisjon.MANUELT_SATT_PÅ_VENT,
                status = AvklaringsbehovStatus.OPPRETTET,
                endringer = listOf(
                    EndringDTO(
                        status = AvklaringsbehovStatus.OPPRETTET,
                        endretAv = "Saksbehandler",
                        tidsstempel = nå.minusHours(5),
                        frist = venteFrist,
                        årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_UTENLANDSK_VIDEREFORING_AVKLARING
                    ),
                ),
            )
        )

        sendBehandlingFlytStoppetHendelse(hendelse2)

        val oppgaver = hentOppgaverForBehandling(behandlingsref)

        assertThat(oppgaver).hasSize(1).first()
            .extracting(OppgaveDto::påVentÅrsak, OppgaveDto::påVentTil)
            .containsExactly(ÅrsakTilSettPåVent.VENTER_PÅ_UTENLANDSK_VIDEREFORING_AVKLARING.toString(), venteFrist)

        // Ventebehovet avsluttes
        val hendelse3 = hendelse2.copy(
            erPåVent = false,
            avklaringsbehov = hendelse2.avklaringsbehov.map {
                if (it.avklaringsbehovDefinisjon == Definisjon.MANUELT_SATT_PÅ_VENT) {
                    it.copy(
                        endringer = it.endringer + EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(4),
                            frist = null,
                            årsakTilSattPåVent = null,
                        ),
                    )
                } else it
            })

        sendBehandlingFlytStoppetHendelse(hendelse3)

        val oppgaver2 = hentOppgaverForBehandling(behandlingsref)

        assertThat(oppgaver2).hasSize(1).first()
            .extracting(OppgaveDto::påVentÅrsak, OppgaveDto::påVentTil)
            .containsExactly(null, null)
    }

    private fun hentOppgaverForBehandling(
        behandlingsref: BehandlingReferanse
    ): List<OppgaveDto> = dataSource.transaction { connection ->
        OppgaveRepository(connection).hentOppgaver(
            referanse = behandlingsref.referanse,
        )
    }


    @Test
    fun `Ved gjenåpning skal oppgaven bli reservert på personen som løste avklaringsbehovet`() {
        val (oppgaveId, saksnummer, behandlingsref) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name)
        )

        val nå = LocalDateTime.now()

        val hendelse = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(2)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(1)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                            årsakTilRetur = listOf(
                                ÅrsakTilRetur(
                                    ÅrsakTilReturKode.MANGELFULL_BEGRUNNELSE
                                )
                            ),
                            begrunnelse = "xxx",
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå
                        )
                    )
                ),
            )
        )

        //Utfør
        sendBehandlingFlytStoppetHendelse(hendelse)

        val oppgave = hentOppgave(oppgaveId)
        assertThat(oppgave.reservertAv).isEqualTo("Saksbehandler")
    }

    private fun sendBehandlingFlytStoppetHendelse(hendelse: BehandlingFlytStoppetHendelse) {
        dataSource.transaction { connection ->
            OppdaterOppgaveService(
                graphClient,
                UnleashService(FakeUnleash().apply {
                    enableAll()
                }),
                veilarbarboppfolgingKlient,
                sykefravarsoppfolgingKlient,
                enhetService,
                OppgaveRepository(connection),
                FlytJobbRepository(connection)
            ).oppdaterOppgaver(hendelse.tilOppgaveOppdatering())
        }
    }

    private fun sendDokumentFlytStoppetHendelse(hendelse: DokumentflytStoppetHendelse) {
        dataSource.transaction { connection ->
            OppdaterOppgaveService(
                graphClient,
                UnleashService(FakeUnleash().apply {
                    enableAll()
                }),
                veilarbarboppfolgingKlient,
                sykefravarsoppfolgingKlient,
                enhetService,
                OppgaveRepository(connection),
                FlytJobbRepository(connection)
            ).oppdaterOppgaver(hendelse.tilOppgaveOppdatering())
        }
    }


    private val ENHET_NAV_LØRENSKOG = "0230"
    private fun opprettOppgave(
        saksnummer: String = "123",
        behandlingRef: UUID = UUID.randomUUID(),
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
        behandlingstype: Behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
        enhet: String = ENHET_NAV_LØRENSKOG,
        oppfølgingsenhet: String? = null,
        veilederArbeid: String? = null,
        veilederSykdom: String? = null,
    ): Triple<OppgaveId, Saksnummer, BehandlingReferanse> {
        val oppgaveDto = OppgaveDto(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovKode = avklaringsbehovKode.kode,
            status = status,
            behandlingstype = behandlingstype,
            opprettetAv = "Kelvin",
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            opprettetTidspunkt = LocalDateTime.now(),
        )
        val oppgaveId = dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
        return Triple(oppgaveId, Saksnummer(saksnummer), BehandlingReferanse(behandlingRef))
    }

    private fun hentOppgave(oppgaveId: OppgaveId): OppgaveDto {
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).hentOppgave(oppgaveId)
        }
    }

    val graphClient = object : IMsGraphClient {
        override fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf {
            return MemberOf(
                groups = listOf(
                    Group(name = "0000-GA-ENHET_$ENHET_NAV_LØRENSKOG", id = UUID.randomUUID()),
                )
            )
        }

        override fun hentFortroligAdresseGruppe(currentToken: String): MemberOf {
            TODO("Not yet implemented")
        }
    }

    val veilarbarboppfolgingKlient = object : IVeilarbarboppfolgingKlient {
        override fun hentVeileder(personIdent: String) = null
    }

    val sykefravarsoppfolgingKlient = object : ISykefravarsoppfolgingKlient {
        override fun hentVeileder(personIdent: String) = null
    }

    val enhetService = object : IEnhetService {
        override fun hentEnheter(currentToken: String, ident: String): List<String> {
            TODO("Not yet implemented")
        }

        override fun utledEnhetForOppgave(
            avklaringsbehovKode: AvklaringsbehovKode,
            fnr: String?
        ): EnhetForOppgave {
            return EnhetForOppgave(ENHET_NAV_LØRENSKOG, null)
        }

        override fun harFortroligAdresse(personIdent: String?): Boolean {
            return false
        }
    }
}
