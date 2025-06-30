package no.nav.aap.oppgave.mottattdokument

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.AfterTest

class MottattDokumentRepositoryTest {

    private val dataSource = InitTestDatabase.freshDatabase()

    private val behandlingRef = UUID.randomUUID()
    private val dok1 = dokument(behandlingRef)
    private val dok2 = dokument(behandlingRef)
    private val dok3 = dokument(behandlingRef)

    @AfterTest
    fun tearDown() {
        @Suppress("SqlWithoutWhere")
        dataSource.transaction {
            it.execute("DELETE FROM MOTTATT_DOKUMENT")
        }
    }

    @Test
    fun `skal lagre og hente ut to ukvitterte dokumenter`() {
        return dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))

            val ukvitterteDokumenter =
                repository.hentUkvitterteDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING)

            Assertions.assertThat(ukvitterteDokumenter.size).isEqualTo(2)
        }
    }

    @Test
    fun `skal kun hente ukvitterte dokumenter`() {
        return dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))
            repository.kvitterDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING, "saksbehandler")

            val ukvitterteDokumenter =
                repository.hentUkvitterteDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING)

            Assertions.assertThat(ukvitterteDokumenter.size).isEqualTo(0)
        }
    }

    @Test
    fun `skal ikke lagre eksisterende dokumenter på nytt`() {
        return dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))

            val ukvitterteDokumenter =
                repository.hentUkvitterteDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING)

            Assertions.assertThat(ukvitterteDokumenter.size).isEqualTo(2)

            repository.lagreDokumenter(listOf(dok1, dok2))

            Assertions.assertThat(ukvitterteDokumenter.size).isEqualTo(2)
        }
    }

    @Test
    fun `skal lagre nytt dokument men ikke eksisterende kvitterte dokumenter`() {
        return dataSource.transaction { connection ->
            val repository = MottattDokumentRepository(connection)

            repository.lagreDokumenter(listOf(dok1, dok2))
            repository.kvitterDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING, "saksbehandler")
            val ukvitterteDokumenter = repository.hentUkvitterteDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING)

            Assertions.assertThat(ukvitterteDokumenter.size).isEqualTo(0)

            repository.lagreDokumenter(listOf(dok1, dok2, dok3))
            val ukvitterteDokumenterMedNyttDokument = repository.hentUkvitterteDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING)

            Assertions.assertThat(ukvitterteDokumenterMedNyttDokument.size).isEqualTo(1)
        }
    }

    private fun dokument(behandlingRef: UUID) =
        MottattDokument(
            type = MottattDokumentType.LEGEERKLÆRING,
            behandlingRef = behandlingRef,
            referanse = UUID.randomUUID().toString(),
        )
}