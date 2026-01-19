package no.nav.aap.oppgave.oppgaveliste

import io.getunleash.FakeUnleash
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.sorterOppgaver
import no.nav.aap.oppgave.unleash.UnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(Fakes::class)
class OppgavelisteServiceTest {
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

    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    @Test
    fun `sorter oppgaveliste`() {
        val sorterteDatoer = listOf<LocalDateTime>(
            LocalDateTime.of(2027, 1, 1, 1, 1),
            LocalDateTime.of(2026, 1, 1, 1, 1),
            LocalDateTime.of(2025, 1, 1, 1, 1),
            LocalDateTime.of(2022, 1, 1, 1, 1),
        )
        val sortertePersonIdenter = listOf<String>(
            "10029044887",
            "10089944887",
            "01015644887",
            "11038044887"
        )
        val oppgaveliste = listOf<OppgaveDto>(
            opprettOppgave(behandlingOpprettet = sorterteDatoer[3], personIdent = sortertePersonIdenter[3]),
            opprettOppgave(behandlingOpprettet = sorterteDatoer[1], personIdent = sortertePersonIdenter[1]),
            opprettOppgave(behandlingOpprettet = sorterteDatoer[0], personIdent = sortertePersonIdenter[0]),
            opprettOppgave(behandlingOpprettet = sorterteDatoer[2], personIdent = sortertePersonIdenter[2])
        )

        // sorter etter behandling opprettet i synkende rekkefølge
        val sorterteDatoerFraOppgaveliste =
            oppgaveliste.sorterOppgaver(OppgavelisteSortering.BEHANDLING_OPPRETTET, null).map { it.behandlingOpprettet }
        assertEquals(sorterteDatoer, sorterteDatoerFraOppgaveliste)

        // sorter etter behandlingOpprettet i stigende rekkefølge
        val sorterteDatoerFraOppgavelisteDescending = oppgaveliste.sorterOppgaver(
            OppgavelisteSortering.BEHANDLING_OPPRETTET,
            OppgavelisteSorteringRekkefølge.ASCENDING
        ).map { it.behandlingOpprettet }
        assertEquals(sorterteDatoer.reversed(), sorterteDatoerFraOppgavelisteDescending)

        // sorter etter personident i synkende rekkefølge
        val sorterteIdenterFraOppgaveliste =
            oppgaveliste.sorterOppgaver(OppgavelisteSortering.BEHANDLING_OPPRETTET, null).map { it.personIdent }
        assertEquals(sortertePersonIdenter, sorterteIdenterFraOppgaveliste)

        // sorter etter personident i stigende rekkefølge
        val sorterteIdenterFraOppgavelisteDescending = oppgaveliste.sorterOppgaver(
            OppgavelisteSortering.BEHANDLING_OPPRETTET,
            OppgavelisteSorteringRekkefølge.ASCENDING
        ).map { it.personIdent }
        assertEquals(sortertePersonIdenter.reversed(), sorterteIdenterFraOppgavelisteDescending)
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
        behandlingOpprettet: LocalDateTime = LocalDateTime.now().minusDays(3),
        veilederArbeid: String? = null,
        veilederSykdom: String? = null,
        utløptVentefrist: LocalDate? = null,
        personIdent: String? = null,
    ): OppgaveDto {
        val oppgaveDto = OppgaveDto(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            behandlingOpprettet = behandlingOpprettet,
            avklaringsbehovKode = avklaringsbehovKode.kode,
            status = status,
            behandlingstype = behandlingstype,
            opprettetAv = "Kelvin",
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            opprettetTidspunkt = LocalDateTime.now(),
            utløptVentefrist = utløptVentefrist,
            personIdent = personIdent
        )
        dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
        return oppgaveDto
    }
}