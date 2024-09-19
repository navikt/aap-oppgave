package no.nav.aap.oppgave

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.opprett.AvklaringsbehovDto
import no.nav.aap.oppgave.opprett.AvklaringsbehovhendelseEndring
import no.nav.aap.oppgave.opprett.Avklaringsbehovstatus
import no.nav.aap.oppgave.opprett.Avklaringsbehovtype
import no.nav.aap.oppgave.opprett.BehandlingshistorikkRequest
import no.nav.aap.oppgave.opprett.Behandlingstatus
import no.nav.aap.oppgave.opprett.Behandlingstype
import no.nav.aap.oppgave.opprett.Definisjon
import no.nav.aap.oppgave.plukk.FinnNesteOppgaveDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.server
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
    fun `Opprett, plukk og avslutt oppgave`() {
        val saksnummer = "123456"
        val referanse = UUID.randomUUID()
        val avklaringsbehovtype: Avklaringsbehovtype = Avklaringsbehovtype.AVKLAR_SYKDOM

        // Opprett ny oppgave
        val oppgaveId = opprettOppgave(saksnummer, referanse)
        assertThat(oppgaveId).isNotNull()

        // Plukk neste oppgave
        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave!!.oppgaveId).isEqualTo(oppgaveId!!)

        // Avslutt plukket oppgave
        val oppgaveIder = avsluttOppgave(saksnummer, referanse, avklaringsbehovtype)
        assertThat(oppgaveIder).hasSize(1)
        assertThat(oppgaveIder.first()).isEqualTo(oppgaveId)

        // Sjekk at det ikke er flere oppgaver i køen
        nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()
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

    private fun opprettBehandlingshistorikk(saksnummer: String, referanse: UUID): BehandlingshistorikkRequest {
        return BehandlingshistorikkRequest(
            personident = "01010012345",
            saksnummer = saksnummer,
            referanse = referanse.toString(),
            behandlingType = Behandlingstype.Førstegangsbehandling,
            status = Behandlingstatus.OPPRETTET,
            opprettetTidspunkt = LocalDateTime.now(),
            avklaringsbehov = listOf(
                AvklaringsbehovDto(
                    definisjon = Definisjon(
                        type = Avklaringsbehovtype.AVKLAR_SYKDOM.kode
                    ),
                    status = Avklaringsbehovstatus.OPPRETTET,
                    endringer = listOf(
                        AvklaringsbehovhendelseEndring(
                            status = Avklaringsbehovstatus.OPPRETTET,
                            tidsstempel = LocalDateTime.now(),
                            endretAv = "Kelvin"
                        )
                    )
                )
            )
        )
    }

    private fun opprettOppgave(saksnummer: String, referanse: UUID): OppgaveId? {
        val request = opprettBehandlingshistorikk(saksnummer, referanse)
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

    private fun avsluttOppgave(saksnummer: String, referanse: UUID, avklaringsbehovtype: Avklaringsbehovtype): List<OppgaveId> {
        val oppgaveIder: List<OppgaveId>? = client.post(
            URI.create("http://localhost:8080/avslutt-oppgave"),
            PostRequest(body = AvsluttOppgaveDto(
                saksnummer = saksnummer,
                referanse = referanse,
                journalpostId = null,
                avklaringsbehovtype = avklaringsbehovtype)
            )
        )
        return oppgaveIder!!
    }

    companion object {
        private val postgres = postgreSQLContainer()
        private val fakes = Fakes(azurePort = 8081)

        private val dbConfig = DbConfig(
            database = postgres.databaseName,
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )

        private val client = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "oppgave"),
            tokenProvider = ClientCredentialsTokenProvider
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

