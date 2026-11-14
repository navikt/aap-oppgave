package no.nav.aap.oppgave.prosessering

import io.getunleash.FakeUnleash
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbType
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.ENHET_NAV_LØRENSKOG
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.ReturInfo
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiGateway
import no.nav.aap.oppgave.opprettOppgave
import no.nav.aap.oppgave.unleash.UnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.verdityper.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(Fakes::class)
class FjernOppgaverFraDeaktiverteBrukereJobbTest {
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

            // Registrerer statistikk-jobbtypen slik at hentJobberForSak/hentJobberForBehandling
            // klarer å parse jobb-rader tilbake i testene
            JobbType.leggTil(StatistikkHendelseJobb)
        }
    }

    private lateinit var dataSource: TestDataSource
    private val TEST_SAKSNUMMER = Saksnummer("123456789")
    private val TEST_BEHANDLINGREF = UUID.randomUUID().let(::BehandlingReferanse)


    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    private fun opprettOppgaveWrapper(
        personIdent: String = "12345678901",
        saksnummer: String = TEST_SAKSNUMMER.toString(),
        behandlingRef: UUID = TEST_BEHANDLINGREF.referanse,
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
        enhet: String = ENHET_NAV_LØRENSKOG,
        oppfølgingsenhet: String? = null,
        veilederArbeid: String? = null,
        veilederSykdom: String? = null,
        behandlingOpprettet: LocalDateTime = LocalDateTime.now(),
        harUlesteDokumenter: Boolean = false,
        påVentTil: LocalDate? = null,
        påVentÅrsak: String? = null,
        venteBegrunnelse: String? = null,
        returInformasjon: ReturInfo? = null,
    ): OppgaveId = opprettOppgave(
        personIdent = personIdent,
        saksnummer = saksnummer,
        behandlingRef = behandlingRef,
        status = status,
        avklaringsbehovKode = avklaringsbehovKode,
        enhet = enhet,
        oppfølgingsenhet = oppfølgingsenhet,
        veilederArbeid = veilederArbeid,
        veilederSykdom = veilederSykdom,
        behandlingOpprettet = behandlingOpprettet,
        harUlesteDokumenter = harUlesteDokumenter,
        påVentTil = påVentTil,
        påVentÅrsak = påVentÅrsak,
        venteBegrunnelse = venteBegrunnelse,
        returInformasjon = returInformasjon,
        dataSource = dataSource,
    )

    @Test
    fun `Reservasjon fjernes for ansatt som har sluttet`() {
        val oppgaveId = opprettOppgaveWrapper()

        dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverOppgave(
                oppgaveId = OppgaveId(oppgaveId.id, oppgaveId.versjon),
                reservertAvIdent = "000011111",
                endretAvIdent = "KELVIN",
                reservertAvNavn = "Testine Testesen"
            )
        }
        val oppgave = hentOppgave(oppgaveId)

        assertEquals("000011111", oppgave.reservertAv, "Reservasjon skal fjernes for ansatt som har sluttet")

        dataSource.transaction {
            FjernOppgaverFraDeaktiverteBrukereJobb(
                OppgaveRepository(it),
                FlytJobbRepository(it),
                NomApiGateway.withClientCredentialsRestClient()
            ).utfør(JobbInput(FjernOppgaverFraDeaktiverteBrukereJobb))
        }

        val oppgaveEtterJobb = hentOppgave(oppgaveId)

        assertEquals(null, oppgaveEtterJobb.reservertAv, "Reservasjon skal fjernes for ansatt som har sluttet")
    }

    @Test
    fun `Reservasjon beholdes for aktiv ansatt`() {
        val oppgaveId = opprettOppgaveWrapper()

        dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverOppgave(
                oppgaveId = OppgaveId(oppgaveId.id, oppgaveId.versjon),
                reservertAvIdent = "000022222",
                endretAvIdent = "KELVIN",
                reservertAvNavn = "Testine Testesen"
            )
        }
        val oppgave = hentOppgave(oppgaveId)

        assertEquals("000022222", oppgave.reservertAv, "Reservasjon skal beholdes for aktiv ansatt")

        dataSource.transaction {
            FjernOppgaverFraDeaktiverteBrukereJobb(
                OppgaveRepository(it),
                FlytJobbRepository(it),
                NomApiGateway.withClientCredentialsRestClient()
            ).utfør(JobbInput(FjernOppgaverFraDeaktiverteBrukereJobb))
        }

        val oppgaveEtterJobb = hentOppgave(oppgaveId)

        assertEquals("000022222", oppgaveEtterJobb.reservertAv, "Reservasjon skal beholdes for aktiv ansatt")
    }

    @Test
    fun `Oppgaver som ikke er reservert forblir uendret`() {
        val oppgaveId = opprettOppgaveWrapper()
        val oppgave = hentOppgave(oppgaveId)

        assertEquals(null, oppgave.reservertAv)

        dataSource.transaction {
            FjernOppgaverFraDeaktiverteBrukereJobb(
                OppgaveRepository(it),
                FlytJobbRepository(it),
                NomApiGateway.withClientCredentialsRestClient()
            ).utfør(JobbInput(FjernOppgaverFraDeaktiverteBrukereJobb))
        }

        val oppgaveEtterJobb = hentOppgave(oppgaveId)
        assertEquals(null, oppgaveEtterJobb.reservertAv, "Oppgaven skal forbli uendret for åpne oppgaver uten reservasjon")
    }

    @Test
    fun `Flere oppgaver reservert til deaktivert bruker skal avreserveres`() {
        val oppgaveId1 = opprettOppgaveWrapper(personIdent = "123456789", saksnummer = "1212121212", behandlingRef = UUID.randomUUID())
        val oppgaveId2 = opprettOppgaveWrapper(personIdent = "212121212", saksnummer = "3434343434", behandlingRef = UUID.randomUUID())

        dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverOppgave(
                oppgaveId = OppgaveId(oppgaveId1.id, oppgaveId1.versjon),
                reservertAvIdent = "000011111",
                endretAvIdent = "KELVIN",
                reservertAvNavn = "Testine Testesen"
            )
            OppgaveRepository(connection).reserverOppgave(
                oppgaveId = OppgaveId(oppgaveId2.id, oppgaveId2.versjon),
                reservertAvIdent = "000011111",
                endretAvIdent = "KELVIN",
                reservertAvNavn = "Testine Testesen"
            )

        }
        val oppgave1 = hentOppgave(oppgaveId1)
        val oppgave2 = hentOppgave(oppgaveId2)

        assertEquals("000011111", oppgave1.reservertAv)
        assertEquals("000011111", oppgave2.reservertAv)

        dataSource.transaction {
            FjernOppgaverFraDeaktiverteBrukereJobb(
                OppgaveRepository(it),
                FlytJobbRepository(it),
                NomApiGateway.withClientCredentialsRestClient()
            ).utfør(JobbInput(FjernOppgaverFraDeaktiverteBrukereJobb))

        }
        val oppgave1EtterJobb = hentOppgave(oppgaveId1)
        val oppgave2EtterJobb = hentOppgave(oppgaveId2)

        assertEquals(null, oppgave1EtterJobb.reservertAv, "Reservasjon skal fjernes for ansatt som har sluttet")
        assertEquals(null, oppgave2EtterJobb.reservertAv, "Reservasjon skal fjernes for ansatt som har sluttet")

    }

    private fun hentOppgave(oppgaveId: OppgaveId): Oppgave {
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).hentOppgave(oppgaveId.id)
        }
    }
}