package no.nav.aap.oppgave.mottattdokument

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.InnsendingType
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.AfterTest

class MottattDokumentServiceTest {

    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    private val behandlingRef = UUID.randomUUID()
    private val ident = "saksbehandler"

    @Test
    fun `skal registrere dokumenter som lest`() {
        return dataSource.transaction { connection ->
            val mottattDokumentRepository = MottattDokumentRepository(connection)
            val oppgaveRepository = OppgaveRepository(connection)
            val service = MottattDokumentService(
                mottattDokumentRepository,
                OppgaveRepository(connection),
            )

            mottattDokumentRepository.lagreDokumenter(listOf(dokument(behandlingRef)))
            oppgaveRepository.opprettOppgave(oppgave(behandlingRef))

            val ulesteDokumenter = mottattDokumentRepository.hentUlesteDokumenter(behandlingRef)
            assertThat(ulesteDokumenter.size).isEqualTo(1)

            service.registrerDokumenterLest(behandlingRef, ident)

            val ulesteDokumenterEtterOppdatering = mottattDokumentRepository.hentUlesteDokumenter(behandlingRef)
            assertThat(ulesteDokumenterEtterOppdatering).isEmpty()
        }
    }

    private fun dokument(behandlingRef: UUID) =
        MottattDokument(
            type = InnsendingType.LEGEERKLÆRING.name,
            behandlingRef = behandlingRef,
            referanse = UUID.randomUUID().toString(),
        )

    private fun oppgave(behandlingRef: UUID) = OppgaveDto(
        saksnummer = "1",
        behandlingRef = behandlingRef,
        behandlingOpprettet = LocalDateTime.now().minusDays(3),
        behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
        opprettetTidspunkt = LocalDateTime.now(),
        opprettetAv = "bruker1",
        enhet = "enhet",
        oppfølgingsenhet = "oppfølgingsenhet",
        avklaringsbehovKode = "1000",
        harUlesteDokumenter = true
    )
}