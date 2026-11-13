package no.nav.aap.oppgave.gjenståendesykedager

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.GjenståendeSykedagerGrunnlag
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GjenståendeSykedagerRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    @Test
    fun `lagrer én rad per behandling, er idempotent og oppdaterer ved endring`() {
        val behandlingRef = UUID.randomUUID()
        val annenBehandlingRef = UUID.randomUUID()
        val sykedager = GjenståendeSykedager(
            grunnlag = GjenståendeSykedagerGrunnlag.VEDTAK_I_NY_LØSNING,
            gjenståendeDager = 42,
            vurdertTilOgMed = LocalDate.of(2026, 1, 1),
            maksdato = LocalDate.of(2026, 3, 2),
        )
        val oppdatert = GjenståendeSykedager(GjenståendeSykedagerGrunnlag.MANGLER_MAKSDATO_I_INFOTRYGD, null, null, null)

        dataSource.transaction { connection ->
            val repository = GjenståendeSykedagerRepository(connection)

            repository.lagre(behandlingRef, sykedager)
            repository.lagre(behandlingRef, sykedager)
            assertEquals(sykedager, repository.hent(behandlingRef))
            assertEquals(1, antallRader(connection, behandlingRef))

            repository.lagre(behandlingRef, oppdatert)
            assertEquals(oppdatert, repository.hent(behandlingRef))
            assertEquals(1, antallRader(connection, behandlingRef))

            repository.lagre(annenBehandlingRef, sykedager)
            val perBehandling = repository.hentForBehandlinger(listOf(behandlingRef, annenBehandlingRef, UUID.randomUUID()))
            assertEquals(mapOf(behandlingRef to oppdatert, annenBehandlingRef to sykedager), perBehandling)

            assertNull(repository.hent(UUID.randomUUID()))
            assertEquals(emptyMap(), repository.hentForBehandlinger(emptyList()))
        }
    }

    private fun antallRader(connection: DBConnection, behandlingRef: UUID) =
        connection.queryFirst("SELECT count(*) AS antall FROM gjenstaende_sykedager WHERE behandling_ref = ?") {
            setParams { setUUID(1, behandlingRef) }
            setRowMapper { it.getInt("antall") }
        }
}
