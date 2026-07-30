package no.nav.aap.oppgave.tilbakekreving

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.opprettOppgave
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TilbakekrevingRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()


    @Test
    fun `kan lagre og hente tilbakekrevings vars`() {
        val oppgave =
            opprettOppgave(behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING, dataSource = dataSource)
        val vars = TilbakekrevingVars(
            oppgaveId = oppgave.id,
            beløp = BigDecimal(1000.00),
            url = "http://tilbakekreving.nav.no/oppgave/12345"
        )
        dataSource.transaction { connection ->
            val repository = TilbakekrevingRepository(connection)

            repository.lagre(vars)

            val hentetVars = repository.hent(vars.oppgaveId)

            assertNotNull(hentetVars)
            assertEquals(vars.oppgaveId, hentetVars.oppgaveId)
            assertEquals(vars.beløp.toBigInteger(), hentetVars.beløp.toBigInteger())
            assertEquals(vars.url, hentetVars.url)
        }
    }

}