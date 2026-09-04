package no.nav.aap.oppgave.uførevedtak
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.uføreVedtak.UføreVedtak
import no.nav.aap.oppgave.uføreVedtak.UføreVedtakRepository
import no.nav.aap.oppgave.verdityper.UføreVedtakStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

class UføreVedtakRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    private val behandlingRef = UUID.randomUUID()

    @Test
    fun `skal fjerne tag på nyeste uførevedtak for behandling`() {

        dataSource.transaction { connection ->
            val repository = UføreVedtakRepository(connection)

            val eldreVedtak = UføreVedtak(
                referanse = behandlingRef,
                virkningsdato = LocalDate.of(2026, 1, 1),
                status = UføreVedtakStatus.INNVILGELSE
            )
            val nyesteVedtak = UføreVedtak(
                referanse = behandlingRef,
                virkningsdato = LocalDate.of(2026, 2, 1),
                status = UføreVedtakStatus.ENDRET
            )

            repository.lagreUføreVedtak(behandlingRef, eldreVedtak)
            repository.lagreUføreVedtak(behandlingRef, nyesteVedtak)

            repository.fjernUføreVedtakTag(behandlingRef, "test_bruker")

            val vedtak = repository.hentAktiveUføreVedtakForBehandling(behandlingRef)
            assertEquals(eldreVedtak.status, vedtak?.status);
            assertEquals(eldreVedtak.virkningsdato, vedtak?.virkningsdato);

        }
    }

    @Test
    fun `skal hente nyeste vedtak basert på virkningsdato`() {

        dataSource.transaction { connection ->
            val repository = UføreVedtakRepository(connection)

            val eldreVedtak = UføreVedtak(
                referanse = behandlingRef,
                virkningsdato = LocalDate.of(2026, 1, 1),
                status = UføreVedtakStatus.INNVILGELSE
            )
            val nyesteVedtak = UføreVedtak(
                referanse = behandlingRef,
                virkningsdato = LocalDate.of(2026, 2, 1),
                status = UføreVedtakStatus.ENDRET
            )

            repository.lagreUføreVedtak(behandlingRef, eldreVedtak)
            repository.lagreUføreVedtak(behandlingRef, nyesteVedtak)

            val vedtak = repository.hentAktiveUføreVedtakForBehandling(behandlingRef)
            assertEquals(nyesteVedtak.status, vedtak?.status);
            assertEquals(nyesteVedtak.virkningsdato, vedtak?.virkningsdato);

        }
    }

    @Test
    fun `skal ikke lagre duplikater`() {

        dataSource.transaction { connection ->
            val repository = UføreVedtakRepository(connection)

            val vedtak = UføreVedtak(
                referanse = behandlingRef,
                virkningsdato = LocalDate.of(2026, 1, 1),
                status = UføreVedtakStatus.INNVILGELSE
            )

            repository.lagreUføreVedtak(behandlingRef, vedtak)
            repository.lagreUføreVedtak(behandlingRef, vedtak)

            val antallVedtak = connection.queryList("""
                SELECT * FROM ufore_vedtak WHERE behandling_ref = ?
            """.trimIndent()) {
                setParams {
                    setUUID(1, behandlingRef)
                }
                setRowMapper {
                    it.getUUID("behandling_ref")
                }
            }.size

            assertEquals(1, antallVedtak)
        }
    }
}
