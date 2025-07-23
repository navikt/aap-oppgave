package no.nav.aap.oppgave.markering

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.BehandlingMarkering
import no.nav.aap.oppgave.MarkeringForBehandling
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.AfterTest

class MarkeringRepositoryTest {
    private val dataSource = InitTestDatabase.freshDatabase()

    @AfterTest
    fun tearDown() {
        @Suppress("SqlWithoutWhere")
        dataSource.transaction {
            it.execute("DELETE FROM MARKERING")
        }
    }

    @Test
    fun `kan lagre, hente og slette markeringer på behandling`() {
        val behandlingId = UUID.randomUUID()
        val spesialkompetanseMarkering = BehandlingMarkering(
            markeringType = MarkeringForBehandling.KREVER_SPESIALKOMPETANSE,
            begrunnelse = "begrunnelse",
            opprettetAv = "saksbehandler"
        )

        val hasterMarkering = BehandlingMarkering(
            markeringType = MarkeringForBehandling.HASTER,
            begrunnelse = "begrunnelse",
            opprettetAv = "saksbehandler"
        )

        dataSource.transaction { connection ->
            val markeringRepository = MarkeringRepository(connection)
            // lagre spesialkompetansemarkering
            markeringRepository.oppdaterMarkering(behandlingId, spesialkompetanseMarkering)
            val hentetMarkering = markeringRepository.hentMarkeringerForBehandling(behandlingId)
            assertThat(hentetMarkering).hasSize(1).first().isEqualTo(spesialkompetanseMarkering)

            // lagre hastemarkering
            markeringRepository.oppdaterMarkering(behandlingId, hasterMarkering)
            val markeringer = markeringRepository.hentMarkeringerForBehandling(behandlingId)
            assertThat(markeringer).hasSize(2).containsExactlyInAnyOrder(hasterMarkering, spesialkompetanseMarkering)

            val nyMarkering = BehandlingMarkering(
                markeringType = MarkeringForBehandling.HASTER,
                begrunnelse = "ny haste-markering",
                opprettetAv = "saksbehandler2"
            )
            // ny hastemarkering, den gamle skal skrives over
            markeringRepository.oppdaterMarkering(behandlingId, nyMarkering)
            assertThat(markeringRepository.hentMarkeringerForBehandling(behandlingId)).hasSize(2).containsExactlyInAnyOrder(nyMarkering, spesialkompetanseMarkering)
        }
    }
}