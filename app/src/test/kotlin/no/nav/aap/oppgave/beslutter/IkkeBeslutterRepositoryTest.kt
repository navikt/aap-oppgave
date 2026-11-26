package no.nav.aap.oppgave.beslutter

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IkkeBeslutterRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    @Test
    fun `lagrer, overskriver og skiller mellom tom liste og ukjent`() {
        val behandlingRef = UUID.randomUUID()
        val annenBehandlingRef = UUID.randomUUID()
        val ukjentBehandlingRef = UUID.randomUUID()
        dataSource.transaction { connection ->
            val repository = IkkeBeslutterRepository(connection)

            repository.lagre(behandlingRef, listOf("Z1", "Z2"))
            repository.lagre(behandlingRef, listOf("Z3"))
            repository.lagre(annenBehandlingRef, emptyList())

            val resultat = repository.hentForBehandlinger(listOf(behandlingRef, annenBehandlingRef, ukjentBehandlingRef))
            assertEquals(listOf("Z3"), resultat[behandlingRef])
            assertEquals(emptyList(), resultat[annenBehandlingRef])
            assertTrue(ukjentBehandlingRef !in resultat)
        }
    }

    @Test
    fun `henting uten behandlinger gir tomt resultat`() {
        dataSource.transaction { connection ->
            assertTrue(IkkeBeslutterRepository(connection).hentForBehandlinger(emptyList()).isEmpty())
        }
    }
}
