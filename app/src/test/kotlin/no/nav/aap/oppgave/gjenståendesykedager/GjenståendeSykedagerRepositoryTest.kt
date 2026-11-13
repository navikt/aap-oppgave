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

    private val behandlingRef = UUID.randomUUID()
    private val sykedager = GjenståendeSykedager(
        grunnlag = GjenståendeSykedagerGrunnlag.VEDTAK_I_NY_LØSNING,
        gjenståendeDager = 42,
        vurdertTilOgMed = LocalDate.of(2026, 1, 1),
        maksdato = LocalDate.of(2026, 3, 2),
    )
    private val utenDatoer =
        GjenståendeSykedager(GjenståendeSykedagerGrunnlag.MANGLER_MAKSDATO_I_INFOTRYGD, null, null, null)

    @Test
    fun `lagrer og henter gjenstående sykedager`() {
        dataSource.transaction { connection ->
            val repository = GjenståendeSykedagerRepository(connection)

            repository.lagre(behandlingRef, sykedager)

            assertEquals(sykedager, repository.hent(behandlingRef))
        }
    }

    @Test
    fun `lagrer null-felt som null`() {
        dataSource.transaction { connection ->
            val repository = GjenståendeSykedagerRepository(connection)

            repository.lagre(behandlingRef, utenDatoer)

            assertEquals(utenDatoer, repository.hent(behandlingRef))
        }
    }

    @Test
    fun `lagring av samme verdi flere ganger gir én rad`() {
        dataSource.transaction { connection ->
            val repository = GjenståendeSykedagerRepository(connection)

            repository.lagre(behandlingRef, sykedager)
            repository.lagre(behandlingRef, sykedager)

            assertEquals(sykedager, repository.hent(behandlingRef))
            assertEquals(1, antallRader(connection, behandlingRef))
        }
    }

    @Test
    fun `ny lagring overskriver eksisterende verdi`() {
        dataSource.transaction { connection ->
            val repository = GjenståendeSykedagerRepository(connection)

            repository.lagre(behandlingRef, sykedager)
            repository.lagre(behandlingRef, utenDatoer)

            assertEquals(utenDatoer, repository.hent(behandlingRef))
            assertEquals(1, antallRader(connection, behandlingRef))
        }
    }

    @Test
    fun `henter for flere behandlinger og hopper over ukjente`() {
        val annenBehandlingRef = UUID.randomUUID()

        dataSource.transaction { connection ->
            val repository = GjenståendeSykedagerRepository(connection)
            repository.lagre(behandlingRef, sykedager)
            repository.lagre(annenBehandlingRef, utenDatoer)

            val perBehandling =
                repository.hentForBehandlinger(listOf(behandlingRef, annenBehandlingRef, UUID.randomUUID()))

            assertEquals(mapOf(behandlingRef to sykedager, annenBehandlingRef to utenDatoer), perBehandling)
        }
    }

    @Test
    fun `returnerer null eller tomt resultat når ingenting er lagret`() {
        dataSource.transaction { connection ->
            val repository = GjenståendeSykedagerRepository(connection)

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
