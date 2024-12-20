package no.nav.aap.oppgave

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.DefinisjonDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.dbconnect.transaction
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
import no.nav.aap.oppgave.filter.FilterId
import no.nav.aap.oppgave.plukk.FinnNesteOppgaveDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.produksjonsstyring.AntallOppgaverDto
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.initDatasource
import no.nav.aap.oppgave.server.server
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.io.BufferedWriter
import java.io.FileWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.fail

class OppgaveApiTest {

    @AfterTest
    fun tearDown() {
        resetDatabase()
    }

    @Test
    fun `Opprett, plukk og avslutt oppgave`() {
        leggInnFilterForTest()
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
        assertThat(oppgave!!.enhet).isEqualTo("superNav!")

        // Plukk neste oppgave
        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave!!.oppgaveId).isEqualTo(oppgave.id)

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
        leggInnFilterForTest()
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
        leggInnFilterForTest()
        val saksnummer = "100001"
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
    fun `Skal forsøke å reservere flere oppgaver dersom bruker ikke har tilgang på den første`() {
        leggInnFilterForTest()
        val saksnummer1 = "100002"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
            ))
        )))

        val saksnummer2 = "100003"
        val referanse2 = UUID.randomUUID()

        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer2, referanse = referanse2, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
            ))
        )))

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse1)
        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave!!.avklaringsbehovReferanse.referanse).isEqualTo(referanse2)

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf()
        nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave!!.avklaringsbehovReferanse.referanse).isEqualTo(referanse1)
    }


    @Test
    fun `Hente filter`() {
        opprettEllerOpdaterFilter(FilterDto(
            navn = "Simpelt filter",
            beskrivelse ="Et enkelt filter for alle oppgave",
            opprettetAv = "test",
            opprettetTidspunkt = LocalDateTime.now(),
        ))

        val alleFilter = hentAlleFilter()

        assertThat(alleFilter).hasSize(1)
    }


    @Test
    fun `Endre filter`() {
        // Opprett filter
        opprettEllerOpdaterFilter(FilterDto(
            navn = "Avklare sykdom i førstegangsbehandling filter",
            beskrivelse = "Avklare sykdom i førstegangsbehandling filter",
            behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING),
            avklaringsbehovKoder = setOf(Definisjon.AVKLAR_SYKDOM.kode.name),
            opprettetAv = "test",
            opprettetTidspunkt = LocalDateTime.now(),
        ))

        // Sjekk lagret filter
        val alleFilter = hentAlleFilter()
        assertThat(alleFilter).hasSize(1)
        val hentetFilter = alleFilter.first()
        assertThat(hentetFilter.behandlingstyper).isEqualTo(setOf(Behandlingstype.FØRSTEGANGSBEHANDLING))
        assertThat(hentetFilter.avklaringsbehovKoder).isEqualTo(setOf(Definisjon.AVKLAR_SYKDOM.kode.name))

        // Oppdater filter
        opprettEllerOpdaterFilter(hentetFilter.copy(
            navn = "Forslå vedtak i revurdering filter",
            behandlingstyper = setOf(Behandlingstype.REVURDERING),
            avklaringsbehovKoder = setOf(Definisjon.FORESLÅ_VEDTAK.kode.name),
            endretAv = "test",
            endretTidspunkt = LocalDateTime.now(),
        ))

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
        opprettEllerOpdaterFilter(FilterDto(
            navn = "Simpelt filter",
            beskrivelse ="Simpelt filter",
            opprettetAv = "test",
            opprettetTidspunkt = LocalDateTime.now(),
        ))

        val alleFilter = hentAlleFilter()
        assertThat(alleFilter).hasSize(1)

        val hentetFilter = alleFilter.first()
        slettFilter(FilterId(hentetFilter.id!!))

        val alleFilterEtterSletting = hentAlleFilter()
        assertThat(alleFilterEtterSletting).hasSize(0)
    }


    @Test
    fun `Hent antall oppgaver uten oppgitt behandlingstype`() {
        val saksnummer = "100004"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer, referanse = referanse, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
            ))
        )))

        val antallOppgaver = hentAntallOppgaver()
        assertThat(antallOppgaver.keys).hasSize(1)
        assertThat(antallOppgaver[Definisjon.AVKLAR_SYKDOM.kode.name]).isEqualTo(1)
    }

    @Test
    fun `Hent antall oppgaver kun for revurdering`() {
        val saksnummer1 = "100005"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_SYKDOM, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
            ))
        )))

        val saksnummer2 = "100006"
        val referanse2 = UUID.randomUUID()

        oppdaterOppgaver(opprettBehandlingshistorikk(saksnummer= saksnummer2, referanse = referanse2, typeBehandling = TypeBehandling.Revurdering, behandlingsbehov = listOf(
            Behandlingsbehov(definisjon = Definisjon.AVKLAR_STUDENT, status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET, endringer = listOf(
                Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
            ))
        )))

        val antallOppgaver = hentAntallOppgaver(Behandlingstype.REVURDERING)
        assertThat(antallOppgaver.keys).hasSize(1)
        assertThat(antallOppgaver[Definisjon.AVKLAR_STUDENT.kode.name]).isEqualTo(1)
    }

    @Test
    fun `Ekporter openapi typer til json`() {
        val openApiDoc =
            requireNotNull(
                client.get(
                    URI.create("http://localhost:8080/openapi.json"),
                    GetRequest()
                ) { body, _ ->
                    String(body.readAllBytes(), StandardCharsets.UTF_8)
                }
            )

        try {
            val writer = BufferedWriter(FileWriter("../openapi.json"))
            writer.write(openApiDoc)

            writer.close()
        } catch (_: Exception) {
            fail()
        }

    }

    private fun hentAntallOppgaver(behandlingstype: Behandlingstype? = null): Map<String, Int> {
        return client.post(
            URI.create("http://localhost:8080/produksjonsstyring/antall-oppgaver"),
            PostRequest(body = AntallOppgaverDto(behandlingstype = behandlingstype))
        )!!
    }

    private fun Definisjon.tilDefinisjonDTO(): DefinisjonDTO {
        return DefinisjonDTO(
            type = this.kode,
            behovType = this.type,
            løsesISteg = this.løsesISteg
        )
    }

    private data class Behandlingsbehov(
        val definisjon: Definisjon,
        val status: no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
        val endringer: List<Endring>
    )

    private data class Endring(
        val status: no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status,
        val endretAv: String = "Kelvin",
    )
    private fun opprettBehandlingshistorikk(saksnummer: String, referanse: UUID, behandlingStatus: Status = Status.OPPRETTET, behandlingsbehov: List<Behandlingsbehov>, typeBehandling: TypeBehandling = TypeBehandling.Førstegangsbehandling): BehandlingFlytStoppetHendelse {
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
            behandlingType = typeBehandling,
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
        val alleFilter = hentAlleFilter()
        val nesteOppgave: NesteOppgaveDto? = client.post(
            URI.create("http://localhost:8080/neste-oppgave"),
            PostRequest(body = FinnNesteOppgaveDto(filterId = alleFilter.first().id!!))
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
                avklaringsbehovKode = definisjon.kode.name
            ))
        )
    }

    private fun hentMineOppgaver(): List<OppgaveDto> {
        return client.get<List<OppgaveDto>>(
            URI.create("http://localhost:8080/mine-oppgaver"),
            GetRequest()
        )!!
    }

    private fun opprettEllerOpdaterFilter(filter: FilterDto): FilterDto? {
        return client.post(
            URI.create("http://localhost:8080/filter"),
            PostRequest(body = filter)
        )
    }

    private fun hentAlleFilter(): List<FilterDto> {
        return client.get<List<FilterDto>>(
            URI.create("http://localhost:8080/filter"),
            GetRequest()
        )!!
    }

    private fun slettFilter(filterId: FilterId): Unit? {
        return client.post(
            URI.create("http://localhost:8080/filter/${filterId.filterId}/slett"),
            PostRequest(body = filterId)
        )
    }

    companion object {
        private val postgres = postgreSQLContainer()
        val fakesConfig: FakesConfig = FakesConfig()
        private val fakes = Fakes(fakesConfig = fakesConfig)

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

        private val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        // Starter server
        private val server = embeddedServer(Netty, port = 8080) {
            server(dbConfig = dbConfig, prometheus = prometheus)
            module(fakes)
        }.start()

        private fun resetDatabase() {
            @Suppress("SqlWithoutWhere")
            initDatasource(dbConfig, prometheus).transaction {
                it.execute("DELETE FROM OPPGAVE_HISTORIKK")
                it.execute("DELETE FROM OPPGAVE")
                it.execute("DELETE FROM FILTER_AVKLARINGSBEHOVTYPE")
                it.execute("DELETE FROM FILTER_BEHANDLINGSTYPE")
                it.execute("DELETE FROM FILTER")
            }
        }

        private fun leggInnFilterForTest() {
            initDatasource(dbConfig, prometheus).transaction {
                it.execute("INSERT INTO FILTER (NAVN, BESKRIVELSE, OPPRETTET_AV, OPPRETTET_TIDSPUNKT) VALUES ('Alle oppgaver', 'Alle oppgaver', 'test', current_timestamp)")
            }
        }

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
    monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server har stoppet")
        fakes.close()
        // Release resources and unsubscribe from events
        application.monitor.unsubscribe(ApplicationStopped) {}
    }
}

