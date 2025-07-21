package no.nav.aap.oppgave

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.filter.FilterId
import no.nav.aap.oppgave.metrikker.prometheus
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.initDatasource
import no.nav.aap.oppgave.server.server
import no.nav.aap.oppgave.verdityper.Behandlingstype
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
                tokenProvider = OnBehalfOfTokenProvider
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
    fun `Endre filter`() {
        // Opprett filter
        opprettEllerOppdaterFilter(
            FilterDto(
                navn = "Avklare sykdom i førstegangsbehandling filter",
                beskrivelse = "Avklare sykdom i førstegangsbehandling filter",
                behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING),
                avklaringsbehovKoder = setOf(Definisjon.AVKLAR_SYKDOM.kode.name),
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
            )
        )

        // Sjekk lagret filter
        val alleFilter = hentAlleFilter()
        assertThat(alleFilter).hasSize(1)
        val hentetFilter = alleFilter.first()
        assertThat(hentetFilter.behandlingstyper).isEqualTo(setOf(Behandlingstype.FØRSTEGANGSBEHANDLING))
        assertThat(hentetFilter.avklaringsbehovKoder).isEqualTo(setOf(Definisjon.AVKLAR_SYKDOM.kode.name))

        // Oppdater filter
        opprettEllerOppdaterFilter(
            hentetFilter.copy(
                navn = "Forslå vedtak i revurdering filter",
                behandlingstyper = setOf(Behandlingstype.REVURDERING),
                avklaringsbehovKoder = setOf(Definisjon.FORESLÅ_VEDTAK.kode.name),
                endretAv = "test",
                endretTidspunkt = LocalDateTime.now(),
            )
        )

        // Sjekk oppdatert filter
        val alleFilterEtterOppdatering = hentAlleFilter()
        assertThat(alleFilterEtterOppdatering).hasSize(1)
        val hentetFilterEtterOppdatering = alleFilterEtterOppdatering.first()
        assertThat(hentetFilterEtterOppdatering.navn).isEqualTo("Forslå vedtak i revurdering filter")
        assertThat(hentetFilterEtterOppdatering.behandlingstyper).isEqualTo(setOf(Behandlingstype.REVURDERING))
        assertThat(hentetFilterEtterOppdatering.avklaringsbehovKoder).isEqualTo(setOf(Definisjon.FORESLÅ_VEDTAK.kode.name))
    }

    @Test
    fun `Slette filter`() {
        opprettEllerOppdaterFilter(
            FilterDto(
                navn = "Simpelt filter",
                beskrivelse = "Simpelt filter",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
            )
        )

        val alleFilter = hentAlleFilter()
        assertThat(alleFilter).hasSize(1)

        val hentetFilter = alleFilter.first()
        slettFilter(FilterId(hentetFilter.id!!))

        val alleFilterEtterSletting = hentAlleFilter()
        assertThat(alleFilterEtterSletting).hasSize(0)
    }

    @Test
    fun `Hente filter`() {
        opprettEllerOppdaterFilter(
            FilterDto(
                navn = "Simpelt filter",
                beskrivelse = "Et enkelt filter for alle oppgave",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
            )
        )

        val alleFilter = hentAlleFilter()

        assertThat(alleFilter).hasSize(1)
    }

    @Test
    fun `Hente filter for enhet`() {
        val filter1 = FilterDto(
            navn = "Simpelt filter",
            beskrivelse = "Et enkelt filter for alle oppgave",
            enheter = setOf("1234"),
            opprettetAv = "test",
            opprettetTidspunkt = LocalDateTime.now(),
        )
        val id1 = opprettEllerOppdaterFilter(
            filter1
        )!!.id

        val id2 = opprettEllerOppdaterFilter(
            FilterDto(
                navn = "Simpelt filter 2",
                beskrivelse = "Et enkelt filter for alle oppgave",
                enheter = setOf("6789"),
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
            )
        )!!.id

        val filtre = hentFilter(listOf("1234", "1235"))

        assertThat(filtre.size).isEqualTo(1)
        assertThat(filtre.find { it.id == id1 }!!.navn).isEqualTo("Simpelt filter")
        assertThat(filtre.find { it.id == id2 }).isNull()
    }

    private fun hentFilter(enheter: List<String>): List<FilterDto> {
        return oboClient().get<List<FilterDto>>(
            URI.create("http://localhost:$port/filter?enheter=${enheter.joinToString("&enheter=")}"),
            GetRequest(currentToken = getOboToken())
        )!!
    }


    private fun slettFilter(filterId: FilterId): Unit? {
        return oboClient().post(
            URI.create("http://localhost:$port/filter/${filterId.filterId}/slett"),
            PostRequest(body = filterId, currentToken = getOboToken())
        )
    }

    private fun opprettEllerOppdaterFilter(filter: FilterDto): FilterDto? {
        return oboClient().post(
            URI.create("http://localhost:$port/filter"),
            PostRequest(body = filter, currentToken = getOboToken())
        )
    }

    private fun hentAlleFilter(): List<FilterDto> {
        return oboClient().get<List<FilterDto>>(
            URI.create("http://localhost:$port/filter"),
            GetRequest(currentToken = getOboToken())
        )!!
    }

}