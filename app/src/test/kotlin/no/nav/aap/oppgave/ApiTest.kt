package no.nav.aap.oppgave

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.opprett.OpprettOppgaveDto
import no.nav.aap.oppgave.plukk.FinnNesteOppgaveDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.verdityper.AvklaringsbehovType
import no.nav.aap.oppgave.verdityper.OppgaveId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class ApiTest {

    @Test
    fun `Opprett og  plukk oppgave`() {
        val saksnummer = "123456"
        val behandlingRef = UUID.randomUUID()
        val avklaringsbehovKode = "1000"

        val oppgaveId = opprettOppgave(saksnummer, behandlingRef, avklaringsbehovKode)
        assertThat(oppgaveId).isNotNull()

        val nesteOppgaveDto = hentNesteOppgave()
        assertThat(nesteOppgaveDto).isNotNull()
        assertThat(nesteOppgaveDto!!.oppgaveId).isEqualTo(oppgaveId!!)

//TODO: fiks senere
//        val oppgaveIder = avsluttOppgave(saksnummer, behandlingRef, avklaringsbehovKode)
//        assertThat(oppgaveIder).hasSize(1)
//        assertThat(oppgaveIder.first()).isEqualTo(oppgaveId)
    }


    @Test
    fun `Hent alle filter`() {
        //TODO endre denne når full funksjonalitet er ferdig utviklet
        val filterListe = client.get<List<FilterDto>>(
            URI.create("http://localhost:8080/filter"),
            GetRequest()
        )
        assertThat(filterListe).hasSize(2)
    }


    private fun opprettOppgave(saksnummer: String, behandlingRef: UUID, avklaringsbehovKode: String): OppgaveId? {
        val request = OpprettOppgaveDto(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            behandlingOpprettet = LocalDateTime.now(),
            avklaringsbehovType = AvklaringsbehovType(avklaringsbehovKode)
        )
        val oppgaveId:OppgaveId? = client.post(
            URI.create("http://localhost:8080/opprett-oppgave"),
            PostRequest(body = request)
        )
        return oppgaveId
    }

    private fun hentNesteOppgave(): NesteOppgaveDto? {
        val nesteOppgave: NesteOppgaveDto? = client.post(
            URI.create("http://localhost:8080/neste-oppgave"),
            PostRequest(body = FinnNesteOppgaveDto(filterId = 1))
        )
        return nesteOppgave
    }

    private fun avsluttOppgave(saksnummer: String, behandlingRef: UUID, avklaringsbehovKode: String): List<OppgaveId> {
        val oppgaveIder: List<OppgaveId>? = client.post(
            URI.create("http://localhost:8080/avslutt-oppgave"),
            PostRequest(body = AvsluttOppgaveDto(
                saksnummer = saksnummer,
                behandlingRef = behandlingRef,
                journalpostId = null,
                avklaringsbehovType = AvklaringsbehovType(avklaringsbehovKode))
            )
        )
        return oppgaveIder!!
    }

    companion object {
        private val postgres = postgreSQLContainer()
        private val fakes = Fakes(azurePort = 8081)

        private val dbConfig = DbConfig(
            database = "test",
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )

        private val client = RestClient(
            config = ClientConfig(scope = "oppgave"),
            tokenProvider = ClientCredentialsTokenProvider,
            responseHandler = DefaultResponseHandler()
        )

        // Starter server
        private val server = embeddedServer(Netty, port = 8080) {
            server(dbConfig = dbConfig)
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

}

private fun postgreSQLContainer(): PostgreSQLContainer<Nothing> {
    val postgres = PostgreSQLContainer<Nothing>("postgres:16")
    postgres.waitingFor(HostPortWaitStrategy().withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS)))
    postgres.start()
    return postgres
}

private fun Application.module(fakes: Fakes) {
    // Setter opp virtuell sandkasse lokalt
    environment.monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server har stoppet")
        fakes.close()
        // Release resources and unsubscribe from events
        application.environment.monitor.unsubscribe(ApplicationStopped) {}
    }
}

