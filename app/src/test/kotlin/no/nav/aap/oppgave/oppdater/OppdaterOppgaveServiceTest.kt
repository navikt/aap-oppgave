package no.nav.aap.oppgave.oppdater

import io.getunleash.FakeUnleash
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.InnsendingId
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.InnsendingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.InnsendingType
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.MottattDokumentDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilRetur
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.ReturStatus
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.enhet.EnhetForOppgave
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.fakes.STRENGT_FORTROLIG_IDENT
import no.nav.aap.oppgave.klienter.msgraph.Group
import no.nav.aap.oppgave.klienter.msgraph.GroupMembers
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.klienter.msgraph.MemberOf
import no.nav.aap.oppgave.klienter.oppfolging.ISykefravarsoppfolgingGateway
import no.nav.aap.oppgave.klienter.oppfolging.IVeilarbarboppfolgingGateway
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.unleash.UnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.postmottak.kontrakt.journalpost.JournalpostId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.AfterTest
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as BehandlingStatus


@ExtendWith(Fakes::class)
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
            mottattDokumenter = listOf(),
            reserverTil = null,
            årsakerTilBehandling = listOf(),
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
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
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
    fun `venteinformasjon fra postmottak-oppgave skal kunne utledes`() {
        val behandlingsref = UUID.randomUUID().let(::BehandlingReferanse)
        val nå = LocalDateTime.now()

        val hendelse = DokumentflytStoppetHendelse(
            journalpostId = JournalpostId(123),
            ident = "personIdent",
            referanse = behandlingsref.referanse,
            behandlingType = no.nav.aap.postmottak.kontrakt.behandling.TypeBehandling.Journalføring,
            status = no.nav.aap.postmottak.kontrakt.behandling.Status.OPPRETTET,
            avklaringsbehov = listOf(
                no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon.DIGITALISER_DOKUMENT,
                    status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                    endringer = listOf(
                        no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO(
                            status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(2)
                        ),
                    )
                )
            ),
            opprettetTidspunkt = nå.minusHours(2),
            saksnummer = null,
            hendelsesTidspunkt = nå.minusHours(2),
        )

        sendDokumentFlytStoppetHendelse(hendelse)

        val åpneOppgaver = hentOppgaverForBehandling(behandlingsref)
        assertThat(åpneOppgaver).hasSize(1)

        val venteFrist = LocalDate.now().plusDays(1)

        val hendelse2 = hendelse.copy(
            avklaringsbehov = hendelse.avklaringsbehov +
                    no.nav.aap.postmottak.kontrakt.hendelse.AvklaringsbehovHendelseDto(
                        avklaringsbehovDefinisjon = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon.MANUELT_SATT_PÅ_VENT,
                        status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO(
                                status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.OPPRETTET,
                                endretAv = "Saksbehandler",
                                tidsstempel = nå.minusHours(1),
                                frist = venteFrist,
                                årsakTilSattPåVent = no.nav.aap.postmottak.kontrakt.hendelse.ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER
                            )
                        )
                    )
        )

        sendDokumentFlytStoppetHendelse(hendelse2)

        val oppgaver = hentOppgaverForBehandling(behandlingsref)
        assertThat(oppgaver).hasSize(1).first()
            .extracting(OppgaveDto::påVentÅrsak, OppgaveDto::påVentTil)
            .containsExactly(no.nav.aap.postmottak.kontrakt.hendelse.ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER.toString(), venteFrist)

        // Ventebehovet avsluttes
        val hendelse3 = hendelse2.copy(
            avklaringsbehov = hendelse2.avklaringsbehov.map {
                if (it.avklaringsbehovDefinisjon == no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon.MANUELT_SATT_PÅ_VENT) {
                    it.copy(
                        endringer = it.endringer + no.nav.aap.postmottak.kontrakt.hendelse.EndringDTO(
                            status = no.nav.aap.postmottak.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå,
                            frist = null,
                            årsakTilSattPåVent = null,
                        )
                    )
                } else it
            }
        )

        sendDokumentFlytStoppetHendelse(hendelse3)
        val oppgaver2 = hentOppgaverForBehandling(behandlingsref)

        assertThat(oppgaver2).hasSize(1).first()
            .extracting(OppgaveDto::påVentÅrsak, OppgaveDto::påVentTil)
            .containsExactly(null, null)
    }

    @Test
    fun `Adressebeskyttelse utledes riktig fra relevante identer på en oppgaveoppdatering`() {
        val (oppgaveId, saksnummer, behandlingsref) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name)
        )
        val nå = LocalDateTime.now()

        val hendelseMedKode6RelatertIdent = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = true,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå,
                            årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER
                        ),
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.VENT_PÅ_OPPFØLGING,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå,
                            årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER,
                            frist = nå.plusDays(2).toLocalDate(),

                            ),
                    )
                ),
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = listOf(STRENGT_FORTROLIG_IDENT),
        )

        sendBehandlingFlytStoppetHendelse(hendelseMedKode6RelatertIdent)

        // Oppgaven skal få oppdatert enhet pga. ny relevant ident med kode 6
        val oppgave = hentOppgave(oppgaveId)
        assertThat(oppgave.enhet).isEqualTo(Enhet.NAV_VIKAFOSSEN.kode)

        val (oppgaveId2, saksnummer2, behandlingsref2) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SAMORDNING_GRADERING.kode.name),
        )

        val hendelseMedKode7RelatertIdent = BehandlingFlytStoppetHendelse(
            personIdent = "1112345678",
            saksnummer = saksnummer2,
            referanse = behandlingsref2,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SAMORDNING_GRADERING,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå,
                        ),
                    )
                )
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = listOf(FORTROLIG_ADRESSE_IDENT),
        )

        sendBehandlingFlytStoppetHendelse(hendelseMedKode7RelatertIdent)

        // Oppgaven skal ha fortrolig adresse pga. ny relevant ident med kode 7
        val fortroligAdresseOppgave = hentOppgave(oppgaveId2)
        assertThat(fortroligAdresseOppgave.enhet).isEqualTo(ENHET_NAV_LØRENSKOG)
        assertThat(fortroligAdresseOppgave.harFortroligAdresse).isTrue()

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
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
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
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
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

    private fun avreserverOppgave(
        oppgaveId: OppgaveId,
        ident: String
    ) {
        dataSource.transaction { connection ->
            OppgaveRepository(connection).avreserverOppgave(oppgaveId, ident)
        }
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
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
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
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )

        //Utfør
        sendBehandlingFlytStoppetHendelse(hendelse)

        val oppgave = hentOppgave(oppgaveId)
        assertThat(oppgave.reservertAv).isEqualTo("Saksbehandler")
    }

    @Test
    fun `Oppgaver skal ikke reserveres til Kelvin selv om Kelvin setter dem på vent`() {
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
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(2)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(1)
                        )
                    )
                ),
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )

        sendBehandlingFlytStoppetHendelse(hendelse)

        // ny hendelse gjenåpner oppgave og setter den på vent
        val hendelse2 = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = true,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå,
                            årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER
                        ),
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.VENT_PÅ_OPPFØLGING,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå,
                            årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER,
                            frist = nå.plusDays(2).toLocalDate(),

                            ),
                    )
                ),
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )

        sendBehandlingFlytStoppetHendelse(hendelse2)

        // skal ikke reserveres til Kelvin
        val oppgave = hentOppgave(oppgaveId)
        assertThat(oppgave.reservertAv).isNull()
    }

    @Test
    fun `Oppgaver på vent skal ikke reserveres på nytt etter avreservering`() {
        val (oppgaveId, saksnummer, behandlingsref) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name)
        )

        val saksbehandler = "saksbehandlerident"

        val nå = LocalDateTime.now()

        // saksbehandler setter sak på vent i sykdomssteget
        val hendelse = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = true,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(2)
                        )
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.VENT_PÅ_OPPFØLGING,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = saksbehandler,
                            tidsstempel = nå,
                            årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER,
                            frist = nå.plusDays(2).toLocalDate(),
                        ),
                    )
                ),
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(hendelse)

        val oppgavePåVent = hentOppgaverForBehandling(behandlingsref).first()
        assertThat(oppgavePåVent.reservertAv).isEqualTo(saksbehandler)
        assertThat(oppgavePåVent.påVentTil).isNotNull()

        // saksbehandler avreserverer
        avreserverOppgave(OppgaveId(oppgavePåVent.id!!, oppgavePåVent.versjon), saksbehandler)

        // ny hendelse
        val nyttMottattDokument = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = true,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(
                MottattDokumentDto(
                    type = InnsendingType.LEGEERKLÆRING,
                    referanse = InnsendingReferanse(id = InnsendingId(UUID.randomUUID())),
                )
            ),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(2)
                        )
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.VENT_PÅ_OPPFØLGING,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = saksbehandler,
                            tidsstempel = nå,
                            årsakTilSattPåVent = ÅrsakTilSettPåVent.VENTER_PÅ_OPPLYSNINGER,
                            frist = nå.plusDays(2).toLocalDate(),
                        ),
                    )
                ),
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(nyttMottattDokument)

        val oppgaveMedMottattDokument = hentOppgaverForBehandling(behandlingsref).first()
        assertThat(oppgaveMedMottattDokument.harUlesteDokumenter).isTrue()

        // Oppgave skal ikke reserveres til saksbehandler igjen
        assertThat(oppgaveMedMottattDokument.påVentTil).isNotNull()
        assertThat(oppgaveMedMottattDokument.reservertAv).isNull()

    }

    @Test
    fun `Oppgaver skal markeres som retur fra veileder når kvalitetssikringsoppgave gjenåpnes`() {
        val nå = LocalDateTime.now()

        val (oppgaveId, saksnummer, behandlingsref) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name)
        )

        // Kvalitetssikrer til veileder til kvalitetssikrer
        val tilKvalitetssikrer = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            mottattDokumenter = listOf(),
            reserverTil = null,
            årsakerTilBehandling = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(10)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Veileder",
                            tidsstempel = nå.minusHours(9)
                        )
                    ),
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.KVALITETSSIKRING,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(5)
                        )
                    )
                )
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(tilKvalitetssikrer)
        val kvalitetssikringsOppgave = hentOppgaverForBehandling(behandlingsref).first { it.status == Status.OPPRETTET }
        assertThat(kvalitetssikringsOppgave.avklaringsbehovKode).isEqualTo(Definisjon.KVALITETSSIKRING.kode.name)


        val returFraKvalitetssikrer = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            mottattDokumenter = listOf(),
            reserverTil = null,
            årsakerTilBehandling = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(10)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Veileder",
                            tidsstempel = nå.minusHours(9)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå.minusHours(6),
                            begrunnelse = "Fordi det er en feil",
                            årsakTilRetur = listOf(
                                ÅrsakTilRetur(
                                    ÅrsakTilReturKode.MANGLENDE_UTREDNING
                                )
                            )
                        )
                    ),

                    ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.KVALITETSSIKRING,
                    status = AvklaringsbehovStatus.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(5)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå.minusHours(5)
                        ),
                    )
                )
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(returFraKvalitetssikrer)

        val returOppgave = hentOppgave(oppgaveId)

        assertThat(returOppgave.returInformasjon?.status).isEqualTo(ReturStatus.RETUR_FRA_KVALITETSSIKRER)
        assertThat(returOppgave.avklaringsbehovKode).isEqualTo(Definisjon.AVKLAR_SYKDOM.kode.name)
        assertThat(returOppgave.returInformasjon?.endretAv).isEqualTo("Kvalitetssikrer")

        val returTilKvalitetssikrer = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            mottattDokumenter = listOf(),
            reserverTil = null,
            årsakerTilBehandling = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(10)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Veileder",
                            tidsstempel = nå.minusHours(9)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
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
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Veileder",
                            tidsstempel = nå.minusHours(5),
                        )
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.KVALITETSSIKRING,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(5)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå.minusHours(5)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(4)
                        ),
                    )
                )
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(returTilKvalitetssikrer)

        val returTilToTrinn = hentOppgaverForBehandling(behandlingsref).first { it.status == Status.OPPRETTET }
        assertThat(returTilToTrinn.returInformasjon?.status).isEqualTo(ReturStatus.RETUR_FRA_VEILEDER)
        assertThat(returTilToTrinn.returInformasjon?.endretAv).isEqualTo("Veileder")
        assertThat(returTilToTrinn.avklaringsbehovKode).isEqualTo(Definisjon.KVALITETSSIKRING.kode.name)
    }

    @Test
    fun `Oppgaver skal markeres som retur fra saksbehandler når beslutteroppgave gjenåpnes`() {
        val nå = LocalDateTime.now()

        val (oppgaveId, saksnummer, behandlingsref) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT.kode.name)
        )

        // Beslutter til veileder til beslutter
        val tilBeslutter = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            mottattDokumenter = listOf(),
            reserverTil = null,
            årsakerTilBehandling = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT,
                    status = AvklaringsbehovStatus.AVSLUTTET,
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
                        )
                    ),
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FATTE_VEDTAK,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(5)
                        )
                    )
                )
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(tilBeslutter)
        val kvalitetssikringsOppgave = hentOppgaverForBehandling(behandlingsref).first { it.status == Status.OPPRETTET }
        assertThat(kvalitetssikringsOppgave.avklaringsbehovKode).isEqualTo(Definisjon.FATTE_VEDTAK.kode.name)


        val returFraBeslutter = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            mottattDokumenter = listOf(),
            reserverTil = null,
            årsakerTilBehandling = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT,
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
                            endretAv = "Beslutter",
                            tidsstempel = nå.minusHours(6),
                            begrunnelse = "Fordi det er en feil",
                            årsakTilRetur = listOf(
                                ÅrsakTilRetur(
                                    ÅrsakTilReturKode.MANGLENDE_UTREDNING
                                )
                            )
                        )
                    ),

                    ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FATTE_VEDTAK,
                    status = AvklaringsbehovStatus.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(5)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Beslutter",
                            tidsstempel = nå.minusHours(5)
                        ),
                    )
                )
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(returFraBeslutter)

        val returOppgave = hentOppgave(oppgaveId)

        assertThat(returOppgave.returInformasjon?.status).isEqualTo(ReturStatus.RETUR_FRA_BESLUTTER)
        assertThat(returOppgave.avklaringsbehovKode).isEqualTo(Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT.kode.name)
        assertThat(returOppgave.returInformasjon?.endretAv).isEqualTo("Beslutter")

        val returTilBeslutter = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            mottattDokumenter = listOf(),
            reserverTil = null,
            årsakerTilBehandling = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT,
                    status = AvklaringsbehovStatus.AVSLUTTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(10)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Veileder",
                            tidsstempel = nå.minusHours(9)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
                            endretAv = "Beslutter",
                            tidsstempel = nå.minusHours(6),
                            begrunnelse = "Fordi det er en feil",
                            årsakTilRetur = listOf(
                                ÅrsakTilRetur(
                                    ÅrsakTilReturKode.MANGLENDE_UTREDNING
                                )
                            )
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(5),
                        )
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.FATTE_VEDTAK,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(5)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Beslutter",
                            tidsstempel = nå.minusHours(5)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(4)
                        ),
                    )
                )
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(returTilBeslutter)

        val returTilToTrinn = hentOppgaverForBehandling(behandlingsref).first { it.status == Status.OPPRETTET }
        assertThat(returTilToTrinn.returInformasjon?.status).isEqualTo(ReturStatus.RETUR_FRA_SAKSBEHANDLER)
        assertThat(returTilToTrinn.returInformasjon?.endretAv).isEqualTo("Saksbehandler")
        assertThat(returTilToTrinn.avklaringsbehovKode).isEqualTo(Definisjon.FATTE_VEDTAK.kode.name)
    }

    @Test
    fun `Trekk søknad-oppgaver kan rutes til båd Nav-kontor og NAY`() {
        val (oppgaveId, saksnummer, behandlingsref) = opprettOppgave(
            status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name)
        )
        val nå = LocalDateTime.now()

        // sak på sykdomssteget
        val tilSykdom = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(2)
                        )
                    )
                ),
            ),
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(tilSykdom)

        val oppgaveSykdom = hentOppgaverForBehandling(behandlingsref).first()
        assertThat(oppgaveSykdom.avklaringsbehovKode).isEqualTo(Definisjon.AVKLAR_SYKDOM.kode.name)

        // søknad trekkes fra sykdomssteget
        val trekkSøknad = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "0",
            hendelsesTidspunkt = nå,
            erPåVent = false,
            årsakerTilBehandling = listOf(),
            mottattDokumenter = listOf(),
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.AVBRUTT,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(2)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVBRUTT,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(1)
                        )
                    )
                ),
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.VURDER_TREKK_AV_SØKNAD,
                    status = AvklaringsbehovStatus.OPPRETTET,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(1)
                        )
                    )
                ),
            ),
            vurderingsbehov = listOf("SØKNAD", "SØKNAD_TRUKKET"),
            årsakTilOpprettelse = "SØKNAD",
            relevanteIdenterPåBehandling = emptyList(),
        )
        sendBehandlingFlytStoppetHendelse(trekkSøknad)

        val oppgaveSøknadTrukket = hentOppgaverForBehandling(behandlingsref).first { it.status != Status.AVSLUTTET }
        assertThat(oppgaveSøknadTrukket.avklaringsbehovKode).isEqualTo(Definisjon.VURDER_TREKK_AV_SØKNAD.kode.name)
        assertThat(oppgaveSøknadTrukket.enhet).isEqualTo(ENHET_NAV_ASKER)
    }


    private fun sendBehandlingFlytStoppetHendelse(hendelse: BehandlingFlytStoppetHendelse) {
        dataSource.transaction { connection ->
            OppdaterOppgaveService(
                graphClient,
                UnleashService(FakeUnleash().apply {
                    enableAll()
                }),
                veilarbarboppfolgingGateway,
                sykefravarsoppfolgingGateway,
                enhetService,
                OppgaveRepository(connection),
                FlytJobbRepository(connection),
                MottattDokumentRepository(connection),
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
                veilarbarboppfolgingGateway,
                sykefravarsoppfolgingGateway,
                enhetService,
                OppgaveRepository(connection),
                FlytJobbRepository(connection),
                MottattDokumentRepository(connection),

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
            OppgaveRepository(connection).hentOppgave(oppgaveId.id)
        }
    }

    val graphClient = object : IMsGraphGateway {
        override fun hentEnhetsgrupper(ident: String, currentToken: OidcToken): MemberOf {
            return MemberOf(
                groups = listOf(
                    Group(name = "0000-GA-ENHET_$ENHET_NAV_LØRENSKOG", id = UUID.randomUUID()),
                )
            )
        }

        override fun hentFortroligAdresseGruppe(ident: String, currentToken: OidcToken): MemberOf {
            return MemberOf()
        }

        override fun hentMedlemmerIGruppe(enhetsnummer: String): GroupMembers {
            return GroupMembers()
        }
    }

    val veilarbarboppfolgingGateway = object : IVeilarbarboppfolgingGateway {
        override fun hentVeileder(personIdent: String) = null
    }

    val sykefravarsoppfolgingGateway = object : ISykefravarsoppfolgingGateway {
        override fun hentVeileder(personIdent: String) = null
    }

    private val FORTROLIG_ADRESSE_IDENT = "11111000000"
    private val ENHET_NAV_ASKER = "0220"
    val enhetService = object : IEnhetService {

        override fun hentEnheter(ident: String, currentToken: OidcToken): List<String> {
            TODO("Not yet implemented")
        }

        override fun utledEnhetForOppgave(
            avklaringsbehovKode: AvklaringsbehovKode,
            ident: String?,
            relevanteIdenter: List<String>,
            saksnummer: String?,
            skalOverstyresTilLokalkontor: Boolean?
        ): EnhetForOppgave {
            if ((relevanteIdenter + ident).contains(STRENGT_FORTROLIG_IDENT)) {
                return EnhetForOppgave(Enhet.NAV_VIKAFOSSEN.kode, null)
            } else if (skalOverstyresTilLokalkontor == true) {
                return EnhetForOppgave(ENHET_NAV_ASKER, null)
            }
            return EnhetForOppgave(ENHET_NAV_LØRENSKOG, null)
        }

        override fun skalHaFortroligAdresse(ident: String?, relevanteIdenter: List<String>): Boolean {
            return ident === FORTROLIG_ADRESSE_IDENT || relevanteIdenter.any { it === FORTROLIG_ADRESSE_IDENT } == true
        }
    }
}
