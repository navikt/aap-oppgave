package no.nav.aap.oppgave.markering

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class MarkeringRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    @Test
    fun `kan lagre, hente og slette markeringer på behandling`() {
        val behandlingId = UUID.randomUUID()
        val spesialkompetanseMarkering = BehandlingMarkering(
            markeringType = MarkeringForBehandling.KREVER_SPESIALKOMPETANSE,
            begrunnelse = "begrunnelseSpesialkompetanse",
            opprettetAv = "saksbehandler"
        )

        val hasterMarkering = BehandlingMarkering(
            markeringType = MarkeringForBehandling.HASTER,
            begrunnelse = "begrunnelseHaster",
            opprettetAv = "saksbehandler"
        )

        dataSource.transaction { connection ->
            val markeringRepository = MarkeringRepository(connection)
            // lagre spesialkompetansemarkering
            markeringRepository.oppdaterMarkering(behandlingId, spesialkompetanseMarkering)
            val hentetMarkering = markeringRepository.hentMarkeringerForBehandling(behandlingId)
            assertThat(hentetMarkering).hasSize(1)
            assertThat(hentetMarkering.first().markeringType).isEqualTo(MarkeringForBehandling.KREVER_SPESIALKOMPETANSE)
            assertThat(hentetMarkering.first().begrunnelse).isEqualTo(spesialkompetanseMarkering.begrunnelse)

            // lagre hastemarkering
            markeringRepository.oppdaterMarkering(behandlingId, hasterMarkering)
            val markeringer = markeringRepository.hentMarkeringerForBehandling(behandlingId)
            assertThat(markeringer).hasSize(2)
            assertThat(markeringer.map { it.markeringType }).containsExactlyInAnyOrder(
                MarkeringForBehandling.KREVER_SPESIALKOMPETANSE,
                MarkeringForBehandling.HASTER
            )
            assertThat(markeringer.map { it.begrunnelse }).containsExactlyInAnyOrder(
                "begrunnelseSpesialkompetanse",
                "begrunnelseHaster"
            )


            val nyMarkering = BehandlingMarkering(
                markeringType = MarkeringForBehandling.HASTER,
                opprettetAv = "saksbehandler2",
            )
            // ny hastemarkering, den gamle skal skrives over
            markeringRepository.oppdaterMarkering(behandlingId, nyMarkering)
            assertThat(markeringRepository.hentMarkeringerForBehandling(behandlingId)).hasSize(2)
            assertThat(
                markeringRepository.hentMarkeringerForBehandling(behandlingId)
                    .first { it.markeringType == MarkeringForBehandling.HASTER }.opprettetAv
            ).isEqualTo("saksbehandler2")
            assertThat(
                markeringRepository.hentMarkeringerForBehandling(behandlingId)
                    .first { it.markeringType == MarkeringForBehandling.HASTER }.begrunnelse
            ).isNull()
            assertThat(
                markeringRepository.hentMarkeringerForBehandling(behandlingId).first().opprettetTidspunkt?.toLocalDate()
            ).isEqualTo(LocalDate.now())
        }
    }
}