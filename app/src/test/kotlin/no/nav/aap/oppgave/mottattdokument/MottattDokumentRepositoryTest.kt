package no.nav.aap.oppgave.mottattdokument

import no.nav.aap.behandlingsflyt.kontrakt.hendelse.InnsendingType
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.AfterTest

class MottattDokumentRepositoryTest {

    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    private val behandlingRef = UUID.randomUUID()
    private val dok1 = dokument(behandlingRef)
    private val dok2 = dokument(behandlingRef)
    private val dok3 = dokument(behandlingRef)


    @Test
    fun `skal lagre og hente ut to uleste dokumenter`() {
        dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))

            val ulesteDokumenter =
                repository.hentUlesteDokumenter(behandlingRef)

            assertThat(ulesteDokumenter.size).isEqualTo(2)
        }
    }

    @Test
    fun `skal kun hente uleste dokumenter`() {
        dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))
            repository.registrerDokumenterSomLest(behandlingRef, "saksbehandler")

            val ulesteDokumenter =
                repository.hentUlesteDokumenter(behandlingRef)

            assertThat(ulesteDokumenter.size).isEqualTo(0)
        }
    }

    @Test
    fun `skal ikke lagre eksisterende dokumenter på nytt`() {
        dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))

            val ulesteDokumenter =
                repository.hentUlesteDokumenter(behandlingRef)

            assertThat(ulesteDokumenter.size).isEqualTo(2)

            repository.lagreDokumenter(listOf(dok1, dok2))

            assertThat(ulesteDokumenter.size).isEqualTo(2)
        }
    }

    @Test
    fun `skal lagre nytt dokument som ulest men ikke eksisterende leste dokumenter`() {
        dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))
            repository.registrerDokumenterSomLest(behandlingRef, "saksbehandler")
            val ulesteDokumenter = repository.hentUlesteDokumenter(behandlingRef)

            assertThat(ulesteDokumenter.size).isEqualTo(0)

            repository.lagreDokumenter(listOf(dok1, dok2, dok3))
            val ulesteDokumenterMedNyttDokument = repository.hentUlesteDokumenter(behandlingRef)

            assertThat(ulesteDokumenterMedNyttDokument.size).isEqualTo(1)
        }
    }

    private fun dokument(behandlingRef: UUID) =
        MottattDokument(
            type = InnsendingType.LEGEERKLÆRING.name,
            behandlingRef = behandlingRef,
            referanse = UUID.randomUUID().toString(),
        )
}