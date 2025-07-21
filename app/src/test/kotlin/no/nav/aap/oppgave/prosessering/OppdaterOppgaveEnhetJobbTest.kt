package no.nav.aap.oppgave.prosessering

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.fakes.FakesConfig
import no.nav.aap.oppgave.fakes.STRENGT_FORTROLIG_IDENT
import no.nav.aap.oppgave.fakes.pdlRequestCounter
import no.nav.aap.oppgave.fakes.pdlBatchSizes
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.postgreSQLContainer
import no.nav.aap.oppgave.server.server
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDateTime
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.Test

// Denne testen kjører i OppgaveApiTest inntil videre
@Disabled("Må skrive om fakes til å bruke singleton - får problemer med parallelle kjøringer")
class OppdaterOppgaveEnhetJobbTest {
    private val dataSource = InitTestDatabase.freshDatabase()

    @Suppress("SqlWithoutWhere")
    @AfterTest
    fun tearDown() {
        dataSource.transaction {
            it.execute("DELETE FROM OPPGAVE_HISTORIKK")
            it.execute("DELETE FROM OPPGAVE")
        }
    }

    @Test
    fun `Skal avreservere og flytte oppgaver til Vikafossen dersom person har fått strengt fortrolig adresse`() {
        val oppgaveId1 = opprettOppgave(personIdent = STRENGT_FORTROLIG_IDENT)
        val oppgaveId2 = opprettOppgave(personIdent = STRENGT_FORTROLIG_IDENT)
        val oppgave2Før = hentOppgave(oppgaveId2)


        dataSource.transaction {
            OppdaterOppgaveEnhetJobb(OppgaveRepository(it), FlytJobbRepositoryImpl(it)).utfør(
                JobbInput(
                    OppdaterOppgaveEnhetJobb
                )
            )
        }

        val oppgave1 = hentOppgave(oppgaveId1)
        assertEquals(Enhet.NAV_VIKAFOSSEN.kode, oppgave1.enhet)
        assertNull(oppgave1.reservertAv)
        assertEquals(oppgave1.endretAv, "Kelvin")
        val oppgave2Etter = hentOppgave(oppgaveId2)
        assertEquals(oppgave2Før, oppgave2Etter)
    }

    @Test
    fun `Skal gjøre 2 kall til PDL når det er mellom 1000 og 2000 identer med maks 1000 i hvert kall`() {
        (1..1500).map { i ->
            val ident = "$i".padStart(11, '0') // Genererer bare ugyldig men unik ident med 11 tegn
            opprettOppgave(personIdent = ident)
        }

        pdlRequestCounter = 0
        pdlBatchSizes.clear()

        dataSource.transaction {
            OppdaterOppgaveEnhetJobb(OppgaveRepository(it), FlytJobbRepositoryImpl(it)).utfør(
                JobbInput(
                    OppdaterOppgaveEnhetJobb
                )
            )
        }

        assertThat(pdlRequestCounter).withFailMessage("Forventet nøyaktig 2 kall til PDL").isEqualTo(2)

        pdlBatchSizes.forEach { batchSize ->
            assertThat(batchSize).withFailMessage("Batchstørrelsen skal være maksimalt 1000, men var $batchSize").isLessThanOrEqualTo(1000)
        }

        assertThat(pdlBatchSizes.sum()).withFailMessage("Totalt antall behandlede identifikatorer skal være 1500").isEqualTo(1500)
    }

    companion object {
        private const val ENHET_NAV_LØRENSKOG = "0230"

        private val postgres = postgreSQLContainer()
        val fakesConfig: FakesConfig = FakesConfig()
        private val fakes = Fakes(fakesConfig = fakesConfig)
        private val dbConfig = DbConfig(
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )

        private val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        // Starter server
        private val server = embeddedServer(Netty, port = 0) {
            server(dbConfig = dbConfig, prometheus = prometheus)
            module(fakes)
        }.start()

        @JvmStatic
        @AfterAll
        fun afterAll() {
            server.stop()
            fakes.close()
            postgres.close()
        }
    }

    private fun opprettOppgave(
        personIdent: String = "12345678901",
        saksnummer: String = "123",
        behandlingRef: UUID = UUID.randomUUID(),
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
        behandlingstype: Behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
        enhet: String = ENHET_NAV_LØRENSKOG,
        oppfølgingsenhet: String? = null,
        veilederArbeid: String? = null,
        veilederSykdom: String? = null,
    ): OppgaveId {
        val oppgaveDto = OppgaveDto(
            personIdent = personIdent,
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovKode = avklaringsbehovKode.kode,
            status = status,
            behandlingstype = behandlingstype,
            opprettetAv = "bruker1",
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            opprettetTidspunkt = LocalDateTime.now()
        )
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
    }

    private fun hentOppgave(oppgaveId: OppgaveId): OppgaveDto {
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
