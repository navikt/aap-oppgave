package no.nav.aap.oppgave

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.DefinisjonDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.fakes.FakesConfig
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.plukk.FinnNesteOppgaveDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.server
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

        // Opprett ny oppgave
        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer, referanse = referanse, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
            ))
        )))

        // Hent oppgaven som ble opprettet
        val oppgave = hentOppgave(saksnummer, referanse, Definisjon.AVKLAR_SYKDOM)
        assertThat(oppgave).isNotNull

        // Plukk neste oppgave
        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave!!.oppgaveId).isEqualTo(oppgave!!.id)

        // Sjekk at oppgave kommer i mine oppgaver listen
        assertThat(hentMineOppgaver().first().id).isEqualTo(oppgave.id)

        // Avslutt oppgave
        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer, referanse = referanse, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET)
            ))
        )))

        // Sjekk at det ikke er flere oppgaver i køen
        nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()
    }

    @Test
    fun `Opprett, oppgave ble automatisk plukket og avslutt oppgave`() {
        val saksnummer = "654321"
        val referanse = UUID.randomUUID()

        // Opprett ny oppgave
        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer, referanse = referanse, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_BISTANDSBEHOV, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
            )),
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET, endretAv = "Lokalsaksbehandler")
            )),
        )))

        // Hent oppgaven som ble opprettet
        val oppgave = hentOppgave(saksnummer, referanse, Definisjon.AVKLAR_BISTANDSBEHOV)
        assertThat(oppgave).isNotNull

        // Sjekk at oppgave kommer i mine oppgaver listen
        assertThat(hentMineOppgaver().first().id).isEqualTo(oppgave!!.id)

        // Avslutt plukket oppgave
        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer, referanse = referanse, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_BISTANDSBEHOV, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET, endretAv = "Lokalsaksbehandler"),
            )),
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET, endretAv = "Lokalsaksbehandler")
            )),
        )))

        // Sjekk at det ikke er flere oppgaver i køen
        val nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()
    }

    @Test
    fun `Skal ikke få plukket oppgave dersom tilgang nektes`() {
        val saksnummer = "123456"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer, referanse = referanse, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
            ))
        )))

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf()
        nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
    }


    @Test
    fun `Hent alle filter`() {
        //TODO endre denne når full funksjonalitet er ferdig utviklet
        val filterListe = client.get<List<FilterDto>>(
            URI.create("http://localhost:8080/filter"),
            GetRequest()
        )
        assertThat(filterListe).hasSize(8)
    }

    private fun Definisjon.tilDefinisjonDTO(): DefinisjonDTO {
        return DefinisjonDTO(
            type = this.kode,
            behovType = this.type,
            løsesISteg = this.løsesISteg
        )
    }

    data class Behandlingsbehov(
        val definisjon: Definisjon,
        val status: no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
        val endringer: List<Endring>
    )

    data class Endring(
        val status: no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status,
        val endretAv: String = "Kelvin",
    )
    private fun opprettBehandlingshistorikk(saksnummer: String, referanse: UUID, behandlingStatus: Status = Status.OPPRETTET, behandlingsbehov: List<Behandlingsbehov>): BehandlingFlytStoppetHendelse {
        val nå = LocalDateTime.now()
        val avklaringsbehovHendelseDtoListe = behandlingsbehov.map { avklaringsbehovHendelse ->
            val endringer = avklaringsbehovHendelse.endringer.mapIndexed { i, endring ->
                EndringDTO(
                    status = endring.status,
                    tidsstempel = nå.minusMinutes(avklaringsbehovHendelse.endringer.size.toLong() - i),
                    endretAv = endring.endretAv
                )
            }
            AvklaringsbehovHendelseDto(
                definisjon = avklaringsbehovHendelse.definisjon.tilDefinisjonDTO(),
                status = avklaringsbehovHendelse.status,
                endringer = endringer
            )
        }
        return BehandlingFlytStoppetHendelse(
            personIdent = "01010012345",
            saksnummer = Saksnummer(saksnummer),
            referanse = BehandlingReferanse(referanse),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            status = behandlingStatus,
            opprettetTidspunkt = nå,
            hendelsesTidspunkt = nå,
            versjon = "1",
            avklaringsbehov = avklaringsbehovHendelseDtoListe,
        )
    }

    private fun oppdaterOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse):Unit? {
         return client.post(
            URI.create("http://localhost:8080/oppdater-oppgaver"),
            PostRequest(body = behandlingFlytStoppetHendelse)
        )
    }

    private fun hentNesteOppgave(): NesteOppgaveDto? {
        val nesteOppgave: NesteOppgaveDto? = client.post(
            URI.create("http://localhost:8080/neste-oppgave"),
            PostRequest(body = FinnNesteOppgaveDto(filterId = 1))
        )
        return nesteOppgave
    }

    private fun hentOppgave(saksnummer: String, referanse: UUID, definisjon: Definisjon): OppgaveDto? {
        return client.post(
            URI.create("http://localhost:8080/hent-oppgave"),
            PostRequest(body = AvklaringsbehovReferanseDto(
                saksnummer = saksnummer,
                referanse = referanse,
                journalpostId = null,
                avklaringsbehovKode = definisjon.kode
            ))
        )
    }

    private fun hentMineOppgaver(): List<OppgaveDto> {
        return client.get<List<OppgaveDto>>(
            URI.create("http://localhost:8080/mine-oppgaver"),
            GetRequest()
        )!!
    }

    companion object {
        private val postgres = postgreSQLContainer()
        val fakesConfig: FakesConfig = FakesConfig()
        private val fakes = Fakes(azurePort = 8081, fakesConfig = fakesConfig)

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

