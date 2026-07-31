package no.nav.aap.oppgave.prosessering

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.fakes.FakesConfig
import no.nav.aap.oppgave.fakes.STRENGT_FORTROLIG_IDENT
import no.nav.aap.oppgave.fakes.pdlBatchSizes
import no.nav.aap.oppgave.fakes.pdlRequestCounter
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlGateway
import no.nav.aap.oppgave.opprettOppgave
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.postgreSQLContainer
import no.nav.aap.oppgave.server.server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import kotlin.test.Test

// Denne testen kjører i OppgaveApiTest inntil videre
@Disabled("Må skrive om fakes til å bruke singleton - får problemer med parallelle kjøringer")
class OppdaterOppgaveEnhetJobbTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    companion object {

        @JvmStatic
        @AfterAll
        fun afterAll() {
            server.stop()
            fakes.close()
            postgres.stop()
        }

        private val postgres = postgreSQLContainer()
        val fakesConfig: FakesConfig = FakesConfig()
        private val fakes = Fakes(fakesConfig = fakesConfig)
        private val dbConfig = DbConfig(
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )

        private val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        private val graphqlGateway = PdlGraphqlGateway.withClientCredentialsRestClient()

        // Starter server
        private val server = embeddedServer(Netty, port = 0) {
            server(dbConfig = dbConfig, prometheus = prometheus)
            module(fakes)
        }.start()

    }

    @Test
    fun `Skal avreservere og flytte oppgaver til Vikafossen dersom person har fått strengt fortrolig adresse`() {
        val oppgaveId1 = opprettOppgave(personIdent = STRENGT_FORTROLIG_IDENT, dataSource = dataSource)
        val oppgaveId2 = opprettOppgave(personIdent = STRENGT_FORTROLIG_IDENT, dataSource = dataSource)
        val oppgave2Før = hentOppgave(oppgaveId2)


        dataSource.transaction {
            OppdaterOppgaveEnhetJobb(OppgaveRepository(it), FlytJobbRepositoryImpl(it), graphqlGateway).utfør(
                JobbInput(
                    OppdaterOppgaveEnhetJobb
                )
            )
        }

        val oppgave1 = hentOppgave(oppgaveId1)
        assertEquals(Enhet.NAV_VIKAFOSSEN.kode, oppgave1.enhet)
        assertNull(oppgave1.reservertAv)
        assertEquals("Kelvin", oppgave1.endretAv)
        val oppgave2Etter = hentOppgave(oppgaveId2)
        assertEquals(oppgave2Før, oppgave2Etter)
    }

    @Test
    fun `Skal gjøre 2 kall til PDL når det er mellom 1000 og 2000 identer med maks 1000 i hvert kall`() {
        (1..1500).map { i ->
            val ident = "$i".padStart(11, '0') // Genererer bare ugyldig men unik ident med 11 tegn
            opprettOppgave(personIdent = ident, dataSource = dataSource)
        }

        pdlRequestCounter = 0
        pdlBatchSizes.clear()

        dataSource.transaction {
            OppdaterOppgaveEnhetJobb(OppgaveRepository(it), FlytJobbRepositoryImpl(it), graphqlGateway).utfør(
                JobbInput(
                    OppdaterOppgaveEnhetJobb
                )
            )
        }

        assertThat(pdlRequestCounter).withFailMessage("Forventet nøyaktig 2 kall til PDL").isEqualTo(2)

        pdlBatchSizes.forEach { batchSize ->
            assertThat(batchSize).withFailMessage("Batchstørrelsen skal være maksimalt 1000, men var $batchSize")
                .isLessThanOrEqualTo(1000)
        }

        assertThat(pdlBatchSizes.sum()).withFailMessage("Totalt antall behandlede identifikatorer skal være 1500")
            .isEqualTo(1500)
    }

    private fun hentOppgave(oppgaveId: OppgaveId): Oppgave {
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).hentOppgave(oppgaveId.id)
        }
    }
}


private fun Application.module(fakes: Fakes) {
    // Setter opp virtuell sandkasse lokalt
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server har stoppet")
        fakes.close()
        // Release resources and unsubscribe from events
        application.monitor.unsubscribe(ApplicationStopped) {}
    }
}
