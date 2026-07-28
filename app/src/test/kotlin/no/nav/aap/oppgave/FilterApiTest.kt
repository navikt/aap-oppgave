package no.nav.aap.oppgave

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureOBOTokenProvider
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.filter.EnhetFilter
import no.nav.aap.oppgave.filter.FilterResponse
import no.nav.aap.oppgave.filter.FilterRepository
import no.nav.aap.oppgave.filter.FilterTypeDto
import no.nav.aap.oppgave.filter.Filtermodus
import no.nav.aap.oppgave.filter.OpprettFilter
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.initDatasource
import no.nav.aap.oppgave.server.server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ExtendWith(Fakes::class)
@Testcontainers
class FilterApiTest {
    companion object {
        private val oboClient = {
            RestClient.withDefaultResponseHandler(
                config = ClientConfig(scope = "oppgave"),
                tokenProvider = AzureOBOTokenProvider,
            )
        }

        // Starter server
        private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

        private val dbConfig = {
            DbConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password
            )
        }

        @JvmStatic
        @Container
        private val postgres = PostgreSQLContainer("postgres:16").waitingFor(HostPortWaitStrategy())
            .withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS))
            .withReuse(false)

        private var port: Int = 0


        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            postgres.start()
            server = embeddedServer(Netty, port = 0) {
                server(dbConfig = dbConfig(), prometheus = prometheus)
            }.start()

            port = server.port()
        }


        @JvmStatic
        @AfterAll
        fun afterAll() {
            server.stop(0, 0)
            postgres.close()
        }
    }

    @BeforeEach
    fun tearDown() {
        resetDatabase()
    }

    private fun resetDatabase() {
        @Suppress("SqlWithoutWhere")
        initDatasource(dbConfig(), prometheus).transaction {
            it.execute("DELETE FROM OPPGAVE_HISTORIKK")
            it.execute("DELETE FROM OPPGAVE")
            it.execute("DELETE FROM FILTER_AVKLARINGSBEHOVTYPE")
            it.execute("DELETE FROM FILTER_BEHANDLINGSTYPE")
            it.execute("DELETE FROM FILTER_ENHET")
            it.execute("DELETE FROM FILTER")
        }
    }

    @Test
    fun `Hente filter`() {
        opprettFiltre()
        val alleFilter = hentAlleFilter()
        assertThat(alleFilter).hasSize(2)
    }

    @Test
    fun `Hente filter for enhet`() {
        opprettFiltre()
        val filtre = hentFilterForEnhet(listOf("1234"))

        assertThat(filtre.size).isEqualTo(2)
        assertThat(filtre.map { it.navn }).containsExactlyInAnyOrder("Simpelt filter", "Filter med enhet")

        val filtre2 = hentFilterForEnhet(listOf("1235"))

        assertThat(filtre2.size).isEqualTo(1)
        assertThat(filtre2.first().navn).isEqualTo("Simpelt filter")
    }

    private fun hentFilterForEnhet(enheter: List<String>): List<FilterResponse> {
        return oboClient().get<List<FilterResponse>>(
            URI.create("http://localhost:$port/filter/v2?enheter=${enheter.joinToString("&enheter=")}"),
            GetRequest(currentToken = getOboToken())
        )!!
    }


    private fun hentAlleFilter(): List<FilterResponse> {
        return oboClient().get<List<FilterResponse>>(
            URI.create("http://localhost:$port/filter/v2"),
            GetRequest(currentToken = getOboToken())
        )!!
    }

    private fun opprettFiltre() {
        initDatasource(dbConfig(), prometheus).transaction { connection ->
            val filterRepo = FilterRepository(connection)
            filterRepo.opprett(
                OpprettFilter(
                    navn = "Simpelt filter",
                    beskrivelse = "Et enkelt filter for alle oppgave",
                    opprettetAv = "test",
                    opprettetTidspunkt = LocalDateTime.now(),
                    type = FilterTypeDto.GENERELL,
                )
            )
            filterRepo.opprett(
                OpprettFilter(
                    navn = "Filter med enhet",
                    beskrivelse = "Et filter for en spesifikk enhet",
                    opprettetAv = "test",
                    opprettetTidspunkt = LocalDateTime.now(),
                    enhetFilter = listOf(EnhetFilter(enhetNr = "1234", filtermodus = Filtermodus.INKLUDER)),
                    type = FilterTypeDto.GENERELL,
                )
            )
        }
    }
}