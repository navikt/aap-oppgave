package no.nav.aap.oppgave

import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.ÅrsakTilOpprettelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingBehandlingsstatus
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilRetur
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.ManglerTilgangException
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureM2MTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureOBOTokenProvider
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.enhet.EnhetOgOversendelse
import no.nav.aap.oppgave.enhet.OppgaveKategori
import no.nav.aap.oppgave.enhet.PersonRequest
import no.nav.aap.oppgave.fakes.AzureTokenGen
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.fakes.FakesConfig
import no.nav.aap.oppgave.fakes.STRENGT_FORTROLIG_IDENT
import no.nav.aap.oppgave.hent.OppgaveVisningsinformasjonResponse
import no.nav.aap.oppgave.hent.VenteInformasjonResponse
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlGateway
import no.nav.aap.oppgave.liste.OppgavelisteRequest
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.plukk.AvreserverOppgaveDto
import no.nav.aap.oppgave.plukk.PlukkOppgaveRequest
import no.nav.aap.oppgave.plukk.PlukkOppgaveResponse
import no.nav.aap.oppgave.prosessering.OppdaterOppgaveEnhetJobb
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.initDatasource
import no.nav.aap.oppgave.server.server
import no.nav.aap.oppgave.søk.SøkRequest
import no.nav.aap.oppgave.søk.SøkResponse
import no.nav.aap.oppgave.tilbakekreving.TilbakekrevingRepository
import no.nav.aap.oppgave.tildel.SaksbehandlerSøkRequest
import no.nav.aap.oppgave.tildel.SaksbehandlerSøkResponse
import no.nav.aap.oppgave.tildel.TildelOppgaveRequest
import no.nav.aap.oppgave.tildel.TildelOppgaveResponse
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import no.nav.aap.oppgave.verdityper.MarkeringHendelseType
import no.nav.aap.oppgave.verdityper.ReturStatus
import no.nav.aap.oppgave.verdityper.ÅrsakTilReturKode
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import no.nav.aap.tilgang.TilgangGateway
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.AfterTest

private const val TEST_IDENT = "01010012345"
private var testFilterId = 0L

@ExtendWith(Fakes::class)
@Testcontainers
class OppgaveApiTest {

    @AfterTest
    fun tearDown() {
        resetDatabase()
    }

    @Test
    fun `Opprett og avslutt oppgave`() {
        val saksnummer = "123456"
        val behandlingsReferanse = BehandlingReferanse(UUID.randomUUID())

        // Opprett ny oppgave
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = behandlingsReferanse.referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        // Hent oppgaven som ble opprettet
        val lokalkontorOppgaver = hentOppgaveliste(
            request = OppgavelisteRequest(
                filterId = testFilterId,
                enheter = setOf("superNav!"),
                paging = Paging()
            )
        )
        assertThat(lokalkontorOppgaver).isNotNull
        assertThat(lokalkontorOppgaver!!.oppgaver).hasSize(1)
        assertThat(lokalkontorOppgaver.oppgaver.first().personOgEnhet.enhet).isEqualTo("superNav!")

        // Hent hele oppgaven
        val oppgaven = hentOppgaveGittBehandlingref(behandlingsReferanse)
        assertThat(oppgaven!!.vurderingsbehov).containsExactly("SØKNAD")


        // Avslutt oppgave
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = behandlingsReferanse.referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET)
                        )
                    )
                )
            )
        )

        // Sjekk at oppgaven er avsluttet
        val avsluttetOppgave = hentOppgaveGittBehandlingref(behandlingsReferanse)
        assertThat(avsluttetOppgave).isNull()
    }

    @Test
    fun `hent og oppdater oppgave for NAY`() {
        val saksnummer = "271828"
        val referanse = UUID.randomUUID()

        // Opprett ny oppgave
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SAMORDNING_GRADERING,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val oppgave = hentOppgaveGittBehandlingref(BehandlingReferanse(referanse))
        assertThat(oppgave).isNotNull
        assertThat(oppgave!!.enhet).isEqualTo("4491")
        assertThat(oppgave.behandlingRef).isEqualTo(referanse)

        assertThat(oppgave.vurderingsbehov).contains("SØKNAD")
        assertThat(oppgave.årsakTilOpprettelse).isEqualTo("SØKNAD")


        // Avslutt oppgave
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SAMORDNING_GRADERING,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET)
                        )
                    )
                )
            )
        )

        // Sjekk at oppgave er avsluttet
        val avsluttetOppgave = hentOppgaveGittOppgaveId(oppgave.oppgaveId())
        assertThat(avsluttetOppgave.status).isEqualTo(no.nav.aap.oppgave.verdityper.Status.AVSLUTTET)
    }

    @Test
    fun `Oppgave skal oppdateres med på vent årsak og dato dersom behandlingen er på vent`() {
        val saksnummer = "45937"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(
                                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                            )
                        )
                    ),
                    Behandlingsbehov(
                        definisjon = Definisjon.VENTE_PÅ_FRIST_EFFEKTUER_11_7,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(
                                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                                påVentTil = LocalDate.now().plusWeeks(2),
                                påVentÅrsak = ÅrsakTilSettPåVent.VENTER_PÅ_MEDISINSKE_OPPLYSNINGER,
                                begrunnelse = "Bedre ting å gjøre"
                            ),
                        )
                    )
                )
            )
        )

        val påVentOppgaver = hentOppgaveVisningsinfo(
            referanse = referanse
        )!!
        assertThat(påVentOppgaver)
            .extracting(OppgaveVisningsinformasjonResponse::påVentInfo)
            .isEqualTo(
                VenteInformasjonResponse(
                    påVentTil = LocalDate.now().plusWeeks(2),
                    påVentÅrsak = ÅrsakTilSettPåVent.VENTER_PÅ_MEDISINSKE_OPPLYSNINGER.name,
                    venteBegrunnelse = "Bedre ting å gjøre"
                )
            )

        val uthentetPåVent = hentOppgaveGittOppgaveId(
            påVentOppgaver.oppgaveId()
        )
        assertThat(uthentetPåVent)
            .extracting(Oppgave::venteBegrunnelse, Oppgave::påVentTil, Oppgave::påVentÅrsak)
            .containsExactly(
                "Bedre ting å gjøre",
                LocalDate.now().plusWeeks(2),
                ÅrsakTilSettPåVent.VENTER_PÅ_MEDISINSKE_OPPLYSNINGER.name
            )

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(
                                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET
                            ),
                        ),
                    ),
                    Behandlingsbehov(
                        definisjon = Definisjon.MANUELT_SATT_PÅ_VENT,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(
                                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                                påVentTil = LocalDate.now().plusWeeks(2),
                                påVentÅrsak = ÅrsakTilSettPåVent.VENTER_PÅ_MEDISINSKE_OPPLYSNINGER
                            ),
                            Endring(
                                status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                            ),
                        )
                    )
                )
            )
        )

        val uthentet = hentOppgaveVisningsinfo(referanse)
        assertThat(uthentet).isNotNull
        assertThat(uthentet!!)
            .extracting(OppgaveVisningsinformasjonResponse::påVentInfo)
            .isNull()

        val påVentOppgaverEtterPå = hentMineOppgaver(kunPåVent = true)
        assertThat(påVentOppgaverEtterPå.oppgaver).isEmpty()
    }

    @Test
    fun `reserver oppgaven automatisk om reservertAv er satt`() {
        val saksnummer = "100002"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                ),
                reserverTil = "U12345"
            )
        )

        val oppgave = hentOppgaveVisningsinfo(
            referanse = referanse
        )!!

        assertThat(oppgave.reservertAvIdent)
            .withFailMessage { "reserverTil skal implisere at oppgaven blir reservert til denne personen" }
            .isEqualTo("U12345")
    }

    @Test
    fun `Tilbakekreving hendelse til oppgave`() {
        val saksnummer = Saksnummer("100002")
        val referanse = BehandlingReferanse(UUID.randomUUID())

        oppdaterTilbakekrevingOppgaver(
            TilbakekrevingsbehandlingOppdatertHendelse(
                personIdent = "12345678910",
                saksnummer = saksnummer,
                behandlingref = referanse,
                behandlingStatus = TilbakekrevingBehandlingsstatus.OPPRETTET,
                sakOpprettet = LocalDateTime.now(),
                totaltFeilutbetaltBeløp = 21321321.toBigDecimal(),
                saksbehandlingURL = "testUrl",
            )
        )

        oppdaterTilbakekrevingOppgaver(
            TilbakekrevingsbehandlingOppdatertHendelse(
                personIdent = "12345678910",
                saksnummer = saksnummer,
                behandlingref = referanse,
                behandlingStatus = TilbakekrevingBehandlingsstatus.TIL_BEHANDLING,
                sakOpprettet = LocalDateTime.now(),
                totaltFeilutbetaltBeløp = 21321321.toBigDecimal(),
                saksbehandlingURL = "testUrl",
            )
        )

        oppdaterTilbakekrevingOppgaver(
            TilbakekrevingsbehandlingOppdatertHendelse(
                personIdent = "12345678910",
                saksnummer = saksnummer,
                behandlingref = referanse,
                behandlingStatus = TilbakekrevingBehandlingsstatus.TIL_GODKJENNING,
                sakOpprettet = LocalDateTime.now(),
                totaltFeilutbetaltBeløp = 21321321.toBigDecimal(),
                saksbehandlingURL = "testUrl",
            )
        )



        dataSource.transaction {
            val oppgaver = OppgaveRepository(it).hentAlleÅpneOppgaver()
            assertThat(oppgaver).hasSize(1)
            assertThat(oppgaver.first().saksnummer).isEqualTo(saksnummer.toString())
            val tilbakekrevingsVars = TilbakekrevingRepository(it).hent(oppgaver.first().id!!)
            assertThat(tilbakekrevingsVars).isNotNull

            assertThat(oppgaver.first().id).isEqualTo(oppgaver.first().id)
            assertThat(oppgaver.first().behandlingstype).isEqualTo(oppgaver.first().behandlingstype)
            assertThat(oppgaver.first().enhet).isEqualTo("4491")
        }


        oppdaterTilbakekrevingOppgaver(
            TilbakekrevingsbehandlingOppdatertHendelse(
                personIdent = "12345678910",
                saksnummer = saksnummer,
                behandlingref = referanse,
                behandlingStatus = TilbakekrevingBehandlingsstatus.AVSLUTTET,
                sakOpprettet = LocalDateTime.now(),
                totaltFeilutbetaltBeløp = 21321321.toBigDecimal(),
                saksbehandlingURL = "testUrl",
            )
        )

        dataSource.transaction {
            val oppgaver = OppgaveRepository(it).hentAlleÅpneOppgaver()
            assertThat(oppgaver).hasSize(0)
        }


    }

    @Test
    fun `Skal få plukket oppgave dersom tilgang`(fakesConfig: FakesConfig) {
        val saksnummer = "100001"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val oppgave = hentOppgaveVisningsinfo(referanse)

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf()
        val nesteOppgave = plukkOppgave(oppgave!!.oppgaveId())
        assertThat(nesteOppgave).isNotNull()
    }

    @Test
    fun `Skal ikke få plukket oppgave dersom tilgang nektes`(fakesConfig: FakesConfig) {
        val saksnummer = "100001"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val oppgave = hentOppgaveVisningsinfo(referanse)

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        assertThrows<ManglerTilgangException> { plukkOppgave(oppgave!!.oppgaveId()) }
    }


    @Test
    fun `Oppgave skal avreserveres dersom tilgang nektes`(fakesConfig: FakesConfig) {
        val saksnummer = "4567"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        // reserverer oppgave
        plukkOppgave(hentOppgaveVisningsinfo(referanse)!!.oppgaveId())
        val reservertOppgaveMedTilgang = hentOppgaveVisningsinfo(
            referanse = referanse,
        )
        assertThat(reservertOppgaveMedTilgang).isNotNull()
        assertThat(reservertOppgaveMedTilgang?.reservertAvIdent).isNotNull()

        // plukk uten tilgang
        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        assertThatThrownBy {
            plukkOppgave(
                reservertOppgaveMedTilgang!!.oppgaveId()
            )
        }
            .isInstanceOf(ManglerTilgangException::class.java)

        // sjekk at reservasjon er fjernet
        val oppgaveUtenReservasjon =
            hentOppgaveGittOppgaveId(reservertOppgaveMedTilgang!!.oppgaveId())
        assertThat(oppgaveUtenReservasjon).isNotNull()
        assertThat(oppgaveUtenReservasjon.reservertAv).isNull()
        assertThat(oppgaveUtenReservasjon.reservertTidspunkt).isNull()
    }

    @Test
    fun `Avreserver liste med oppgaver`() {
        val saksnummer1 = "4567"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val saksnummer2 = "1234"
        val referanse2 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer2, referanse = referanse2, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        // reserverer begge oppgaver
        val oppgave1 = hentOppgaveVisningsinfo(referanse1)
        val oppgave2 = hentOppgaveVisningsinfo(referanse2)
        reserverOppgave(oppgave1!!.oppgaveId(), "saksbehandler1", "saksbehandler1")
        reserverOppgave(oppgave2!!.oppgaveId(), "saksbehandler2", "saksbehandler2")

        // kall endepunkt for avreservering
        val avreserverteOppgaveIds = avreserverOppgaver(listOf(oppgave1.id, oppgave2.id))
        val avreserverteOppgaver = avreserverteOppgaveIds?.map { hentOppgaveGittOppgaveId(it) }

        assertThat(avreserverteOppgaver).hasSize(2)
        assertThat(avreserverteOppgaver?.all { it.reservertAv == null && it.reservertTidspunkt == null }).isTrue()

    }

    @Test
    fun `Kan søke etter saksbehandlere fra MsGraph`() {
        val saksnummer1 = "4567"
        val referanse1 = UUID.randomUUID()

        // ny kontor-oppgavr
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val oppgave = hentOppgaveVisningsinfo(referanse1)

        // Søk på å tildele en 11-5-oppgave skal bare returnere veiledere med tilgang til enheten
        val lokalSaksbehandlere = søkEtterSaksbehandlere("Kontorsen", listOf(oppgave?.id!!))?.saksbehandlere
        assertThat(lokalSaksbehandlere).hasSize(1)
        assertThat(lokalSaksbehandlere?.first()?.navIdent).isEqualTo("KontorVeileder123")

        // Ny NAY-oppgave
        val saksnummer2 = "1234"
        val referanse2 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer2, referanse = referanse2, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.FASTSETT_BEREGNINGSTIDSPUNKT,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )


        val oppgave2 = hentOppgaveVisningsinfo(referanse2)

        // Søk på å tildele en 11-19-oppgave skal bare returnere den NAY-saksbehandleren med enhetstilgang
        val naySaksbehandlere = søkEtterSaksbehandlere("Naysen", listOf(oppgave2?.id!!))?.saksbehandlere
        assertThat(naySaksbehandlere).hasSize(1)
        assertThat(naySaksbehandlere?.first()?.navIdent).isEqualTo("NayVeileder123")

        // Søk på å tildele både en NAY-oppgave og en kontor-oppgave skal returnere kun saksbehandlere som har tilgang til en av dem
        val alleSaksbehandlere = søkEtterSaksbehandlere("Test", listOf(oppgave.id, oppgave2.id))?.saksbehandlere
        assertThat(alleSaksbehandlere).hasSize(2)

        // Når ingen matcher returneres tom liste
        val ingenSaksbehandlere = søkEtterSaksbehandlere("xxxxx", listOf(oppgave.id, oppgave2.id))?.saksbehandlere
        assertThat(ingenSaksbehandlere).isEmpty()

        // Kan søke på fullt navn
        val naySaksbehandler = søkEtterSaksbehandlere("test naysen", listOf(oppgave2.id))?.saksbehandlere
        assertThat(naySaksbehandler).hasSize(1)
        assertThat(naySaksbehandler?.first()?.navIdent).isEqualTo("NayVeileder123")

        // Kan søke på NAV-ident
        val naySaksbehandlerIdent = søkEtterSaksbehandlere("nayveileder123", listOf(oppgave2.id))?.saksbehandlere
        assertThat(naySaksbehandlerIdent).hasSize(1)
        assertThat(naySaksbehandlerIdent?.first()?.navIdent).isEqualTo("NayVeileder123")

    }

    @Test
    fun `Tildeler en liste med oppgaver`() {
        val saksnummer1 = "4567"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val saksnummer2 = "1234"
        val referanse2 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer2, referanse = referanse2, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val oppgave1 = hentOppgaveVisningsinfo(referanse1)
        val oppgave2 = hentOppgaveVisningsinfo(referanse2)

        tildelOppgaver(listOf(oppgave1?.id!!, oppgave2?.id!!), ident = "saksbehandler")

        val oppgave1EtterReservering = hentOppgaveVisningsinfo(referanse1)
        val oppgave2EtterReservering = hentOppgaveVisningsinfo(referanse2)
        assertThat(oppgave1EtterReservering?.reservertAvIdent).isEqualTo("saksbehandler")
        assertThat(oppgave2EtterReservering?.reservertAvIdent).isEqualTo("saksbehandler")

        // kan tildele oppgave på nytt, selv om den nå er reservert av noen
        tildelOppgaver(listOf(oppgave1EtterReservering?.id!!), ident = "saksbehandler2")

        val oppgave1Igjen = hentOppgaveVisningsinfo(referanse1)
        assertThat(oppgave1Igjen?.reservertAvIdent).isEqualTo("saksbehandler2")

    }

    @Test
    fun `Oppdaterer enhet på mislykket forsøk på plukk`(fakesConfig: FakesConfig) {
        val saksnummer = "8910"
        val referanse = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )


        val oppgaveMedGammelEnhet = hentOppgaveVisningsinfo(referanse)
        assertThat(oppgaveMedGammelEnhet).isNotNull()

        // oppdater enhet på oppgave
        val oppgaveMedNyEnhet = oppdaterOgHentOppgave(
            oppgaveId = oppgaveMedGammelEnhet!!.oppgaveId(),
            enhet = "nyEnhet",
            oppfølgingsenhet = "nyOppfølgingsenhet",
        )
        assertThat(oppgaveMedNyEnhet.enhet).isEqualTo("nyEnhet")
        assertThat(oppgaveMedNyEnhet.oppfølgingsenhet).isEqualTo("nyOppfølgingsenhet")

        // plukk uten tilgang
        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        fakesConfig.relaterteIdenterPåBehandling = emptyList()
        assertThatThrownBy { plukkOppgave(oppgaveMedNyEnhet.oppgaveId()) }.isInstanceOf(
            ManglerTilgangException::class.java
        )

        // enhet skal ha blitt oppdatert etter mislykket plukk
        val oppgaveEtterOppdatering = hentOppgaveGittBehandlingref(BehandlingReferanse(referanse))
        assertThat(oppgaveEtterOppdatering).isNotNull()
        assertThat(oppgaveEtterOppdatering!!.enhet).isEqualTo("superNav!")
        assertThat(oppgaveEtterOppdatering.oppfølgingsenhet).isEqualTo("superNav!")
    }

    @Test
    fun `Oppdaterer enhet til vikafossen om relatert ident har fått strengt fortrolig adresse`(fakesConfig: FakesConfig) {
        val saksnummer = "8910"
        val referanse = UUID.randomUUID()

        // BehandlingsflytStoppetHendelse uten relaterte identer
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SAMORDNING_GRADERING,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )


        val oppgaveMedGammelEnhet = hentOppgaveVisningsinfo(referanse)
        assertThat(oppgaveMedGammelEnhet).isNotNull()

        // plukk uten tilgang - det har kommet ny relatert ident på sak fra behandlingsflyt-pip
        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        fakesConfig.relaterteIdenterPåBehandling = listOf(STRENGT_FORTROLIG_IDENT)
        assertThatThrownBy { plukkOppgave(oppgaveMedGammelEnhet!!.oppgaveId()) }.isInstanceOf(
            ManglerTilgangException::class.java
        )

        // enhet skal ha blitt oppdatert med hensyn til relatert ident etter mislykket plukk
        val oppgaveEtterOppdatering = hentOppgaveGittOppgaveId(oppgaveMedGammelEnhet!!.oppgaveId())
        assertThat(oppgaveEtterOppdatering).isNotNull()
        assertThat(oppgaveEtterOppdatering.enhet).isEqualTo(Enhet.NAV_VIKAFOSSEN.kode)
        assertThat(oppgaveEtterOppdatering.oppfølgingsenhet).isNull()
    }

    @Test
    fun `Utleder adressebeskyttelse riktig i søk`() {
        val saksnummer1 = "100002"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val opprettetOppgave = hentOppgaveVisningsinfo(
            referanse = referanse1,
        )

        // sett strengt fortrolig adresse
        oppdaterOgHentOppgave(
            oppgaveId = opprettetOppgave!!.oppgaveId(),
            enhet = Enhet.NAV_VIKAFOSSEN.kode,
        )

        val søkResponseStrengtFortrolig = søkEtterOppgaver(SøkRequest(saksnummer1))
        assertThat(søkResponseStrengtFortrolig?.harAdressebeskyttelse).isTrue()

        // sett fortrolig adresse
        oppdaterOgHentOppgave(
            enhet = ENHET_NAV_LØRENSKOG,
            oppgaveId = hentOppgaveVisningsinfo(referanse1)!!.oppgaveId(),
            harFortroligAdresse = true
        )

        val søkResponseFortroligAdresse = søkEtterOppgaver(SøkRequest(saksnummer1))
        assertThat(søkResponseFortroligAdresse?.harAdressebeskyttelse).isTrue()

        // sett egen ansatt
        oppdaterOgHentOppgave(
            oppgaveId = hentOppgaveVisningsinfo(referanse1)!!.oppgaveId(),
            harFortroligAdresse = false,
            erSkjermet = true
        )

        val søkResponseEgenAnsatt = søkEtterOppgaver(SøkRequest(saksnummer1))
        assertThat(søkResponseEgenAnsatt?.harAdressebeskyttelse).isTrue()

    }

    @Test
    fun `Kan oppdatere oppgave til fortrolig adresse`() {
        val saksnummer1 = "100002"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val oppgaveUtenFortroligAdresse = hentOppgaveVisningsinfo(referanse1)
        assertThat(oppgaveUtenFortroligAdresse).isNotNull()

        // sett fortrolig adresse
        settFortroligAdresseForOppgave(
            oppgaveId = oppgaveUtenFortroligAdresse!!.oppgaveId(), skalHaFortroligAdresse = true
        )

        // hent på nytt
        val oppgaveMedFortroligAdresse = hentOppgaveGittOppgaveId(
            oppgaveUtenFortroligAdresse.oppgaveId()
        )
        assertThat(oppgaveMedFortroligAdresse.harFortroligAdresse).isTrue()
    }


    @Test
    fun `oppgaver skal merkes med returstatus`() {
        val saksnummer1 = "1023005"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                        )
                    )
                )
            )
        )

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET),
                        )
                    )
                )
            )
        )

        // Den ble returnert fra kvalitetssikrer
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET),
                            Endring(
                                no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                                begrunnelse = "xxx",
                                endretAv = "Johannes Johannesen",
                                årsakTilRetur = listOf(ÅrsakTilReturKode.FEIL_LOVANVENDELSE)
                            ),
                        )
                    )
                )
            )
        )

        // Oppgaven er gjenopprettet
        val oppgaven = hentOppgaveVisningsinfo(referanse1)!!

        assertThat(oppgaven).extracting(OppgaveVisningsinformasjonResponse::returInformasjon)
            .isNotNull
            .isEqualTo(
                ReturInformasjonDto(
                    status = ReturStatus.RETUR_FRA_KVALITETSSIKRER,
                    årsaker = listOf(ÅrsakTilReturKode.FEIL_LOVANVENDELSE),
                    begrunnelse = "xxx",
                    endretAv = "Johannes Johannesen",
                )
            )
    }

    // TODO: Flytt denne i egen klasse når fakes er skrevet om
    @Test
    fun `Skal avreservere og flytte oppgaver til Vikafossen dersom person har fått strengt fortrolig adresse`() {
        val oppgaveId1 = opprettOppgave(personIdent = STRENGT_FORTROLIG_IDENT, dataSource = dataSource)
        val oppgaveId2 = opprettOppgave(dataSource = dataSource)
        val oppgave2Før = hentOppgaveGittOppgaveId(oppgaveId2)

        dataSource.transaction {
            OppdaterOppgaveEnhetJobb(
                OppgaveRepository(it),
                FlytJobbRepositoryImpl(it),
                PdlGraphqlGateway.withClientCredentialsRestClient()
            ).utfør(
                JobbInput(
                    OppdaterOppgaveEnhetJobb
                )
            )
        }

        val oppgave1 = hentOppgaveGittOppgaveId(oppgaveId1)
        assertEquals(Enhet.NAV_VIKAFOSSEN.kode, oppgave1.enhet)
        assertNull(oppgave1.reservertAv)
        assertEquals("Kelvin", oppgave1.endretAv)
        val oppgave2Etter = hentOppgaveGittOppgaveId(oppgaveId2)
        assertEquals(oppgave2Før, oppgave2Etter)
    }

    @Test
    fun `oppgaver skal opprettes også når behandlingen har status IVERKSETTES, men ikke når status er AVSLUTTET`() {
        val saksnummer1 = "1023005"
        val referanse1 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.SKRIV_VEDTAKSBREV,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                        )
                    )
                ),
                behandlingStatus = Status.IVERKSETTES
            )
        )
        // Behandlingen er AVSLUTTET, da skal åpne oppgaver lukkes
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer1, referanse = referanse1, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.SKRIV_VEDTAKSBREV,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                        )
                    )
                ),
                behandlingStatus = Status.AVSLUTTET
            )
        )
    }

    @Test
    fun `markeringer skal sendes med i oppgavelistene`() {
        val behandlingref = BehandlingReferanse(UUID.randomUUID())
        val saksnummer = "1023005"
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = behandlingref.referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                        )
                    )
                )
            )
        )

        // legg på markering på behandling
        val markering = MarkeringDto(
            markeringType = MarkeringForBehandling.HASTER,
            begrunnelse = "Haster",
            opprettetTidspunkt = LocalDateTime.now(),
            hendelseType = MarkeringHendelseType.OPPRETTET
        )
        opprettMarkeringHendelse(behandlingref.referanse, markering)

        // reserver og hent mine oppgaver
        plukkOppgave(hentOppgaveGittBehandlingref(behandlingref)?.oppgaveId()!!)
        val mineOppgaver = hentMineOppgaver()
        assertThat(mineOppgaver.oppgaver).hasSize(1)
        assertThat(mineOppgaver.oppgaver.first().oppgavelisteTags.markeringer).hasSize(1)
        assertThat(mineOppgaver.oppgaver.first().oppgavelisteTags.markeringer.first().markeringType).isEqualTo(
            MarkeringForBehandling.HASTER
        )
        assertThat(mineOppgaver.oppgaver.first().oppgavelisteTags.markeringer.first().begrunnelse).isEqualTo(markering.begrunnelse)

        // hent markering fra endepunkt
        val markeringer = hentGjeldendeMarkeringerForBehandling(behandlingref.referanse)
        assertThat(markeringer).hasSize(1)
        assertThat(markeringer?.first()?.markeringType).isEqualTo(MarkeringForBehandling.HASTER)
        assertThat(markeringer?.first()?.begrunnelse).isEqualTo(markering.begrunnelse)
    }

    @Test
    fun `markeringer sendes ikke med i oppgavelistene etter at de er fjernet`() {
        val behandlingref = BehandlingReferanse(UUID.randomUUID())
        val saksnummer = "1023005"
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = behandlingref.referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                        )
                    )
                )
            )
        )

        // legg på markering på behandling
        val markeringOpprettet = MarkeringDto(
            markeringType = MarkeringForBehandling.HASTER,
            begrunnelse = "Haster",
            opprettetTidspunkt = LocalDateTime.now(),
            hendelseType = MarkeringHendelseType.OPPRETTET
        )
        opprettMarkeringHendelse(behandlingref.referanse, markeringOpprettet)

        // reserver
        val hentetOppgave = hentOppgaveVisningsinfo(behandlingref.referanse)
        plukkOppgave(OppgaveId(hentetOppgave?.id!!, hentetOppgave.versjon))

        // fjern markering
        val markeringFjernet = MarkeringDto(
            markeringType = MarkeringForBehandling.HASTER,
            begrunnelse = "Haster",
            opprettetTidspunkt = LocalDateTime.now(),
            hendelseType = MarkeringHendelseType.FJERNET
        )
        opprettMarkeringHendelse(behandlingref.referanse, markeringFjernet)
        val mineOppgaver = hentMineOppgaver()
        assertThat(mineOppgaver.oppgaver).hasSize(1)
        val gjeldendeHastemarkeringer =
            mineOppgaver.oppgaver.first().oppgavelisteTags.markeringer.filter { it.markeringType == MarkeringForBehandling.HASTER }
        assertThat(gjeldendeHastemarkeringer).isEmpty()
    }

    @Test
    fun `hent enhet-status for person`() {
        val personIdent = TEST_IDENT  // Standard ident brukt i opprettBehandlingshistorikk
        val saksnummer = "987654"
        val referanse = UUID.randomUUID()

        // Opprett lokalkontor-oppgave
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        // Hent enhetstatus for personen - skal returnere lokalkontor
        val response = client.post<PersonRequest, EnhetOgOversendelse>(
            URI.create("http://localhost:$port/enhet/status/person"),
            PostRequest(
                body = PersonRequest(ident = personIdent)
            )
        )

        assertThat(response).isNotNull()
        assertThat(response!!.tilstand).isNotNull()
        val tilstand = response.tilstand!!
        assertThat(tilstand.oppgaveKategori).isEqualTo(OppgaveKategori.LOKALKONTOR)
        assertThat(tilstand.enhet).isEqualTo("superNav!")
        assertThat(tilstand.saksnummer).isEqualTo(saksnummer)

        // Opprett NAY-oppgave
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET)
                        )
                    ),
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SAMORDNING_GRADERING,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        // Hent enhet historikk igjen - lokalkontor har fortsatt prioritet
        val response2 = client.post<PersonRequest, EnhetOgOversendelse>(
            URI.create("http://localhost:$port/enhet/status/person"),
            PostRequest(
                body = PersonRequest(ident = personIdent)
            )
        )

        assertThat(response2).isNotNull()
        assertThat(response2!!.tilstand).isNotNull()
        assertThat(response2.tilstand!!.oppgaveKategori).isEqualTo(OppgaveKategori.NAY)
        assertThat(response2.tilstand!!.enhet).isEqualTo("4491")
    }

    private data class Behandlingsbehov(
        val definisjon: Definisjon,
        val status: no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
        val endringer: List<Endring>
    )

    private data class Endring(
        val status: no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status,
        val endretAv: String = "Kelvin",
        val påVentTil: LocalDate? = null,
        val påVentÅrsak: ÅrsakTilSettPåVent? = null,
        val begrunnelse: String? = null,
        val årsakTilRetur: List<ÅrsakTilReturKode> = emptyList(),
    )

    private fun opprettBehandlingshistorikk(
        saksnummer: String,
        referanse: UUID,
        behandlingStatus: Status = Status.OPPRETTET,
        behandlingsbehov: List<Behandlingsbehov>,
        typeBehandling: TypeBehandling = TypeBehandling.Førstegangsbehandling,
        reserverTil: String? = null,
        relaterteIdenter: List<String>? = emptyList()
    ): BehandlingFlytStoppetHendelse {
        val nå = LocalDateTime.now()
        val avklaringsbehovHendelseDtoListe = behandlingsbehov.map { avklaringsbehovHendelse ->
            val endringer = avklaringsbehovHendelse.endringer.mapIndexed { i, endring ->
                EndringDTO(
                    status = endring.status,
                    tidsstempel = nå.minusMinutes(avklaringsbehovHendelse.endringer.size.toLong() - i),
                    endretAv = endring.endretAv,
                    frist = endring.påVentTil,
                    årsakTilSattPåVent = endring.påVentÅrsak,
                    begrunnelse = endring.begrunnelse,
                    årsakTilRetur = endring.årsakTilRetur.map {
                        ÅrsakTilRetur(
                            no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilReturKode.valueOf(
                                it.name
                            )
                        )
                    }
                )
            }
            AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = avklaringsbehovHendelse.definisjon,
                status = avklaringsbehovHendelse.status,
                endringer = endringer
            )
        }
        return BehandlingFlytStoppetHendelse(
            personIdent = TEST_IDENT,
            saksnummer = Saksnummer(saksnummer),
            referanse = BehandlingReferanse(referanse),
            behandlingType = typeBehandling,
            status = behandlingStatus,
            opprettetTidspunkt = nå,
            hendelsesTidspunkt = nå,
            versjon = "1",
            avklaringsbehov = avklaringsbehovHendelseDtoListe,
            årsakerTilBehandling = listOf("SØKNAD"),
            relevanteIdenterPåBehandling = relaterteIdenter,
            erPåVent = avklaringsbehovHendelseDtoListe.any { it.avklaringsbehovDefinisjon.erVentebehov() && it.status != no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET },
            uføreVedtak = null,
            mottattDokumenter = listOf(),
            reserverTil = reserverTil,
            vurderingsbehov = listOf("SØKNAD"),
            årsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD
        )
    }

    private fun oppdaterOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse): Unit? {
        return client.post(
            URI.create("http://localhost:$port/oppdater-oppgaver"),
            PostRequest(body = behandlingFlytStoppetHendelse)
        )
    }

    private fun oppdaterTilbakekrevingOppgaver(tilbakekrevingsbehandlingOppdatertHendelse: TilbakekrevingsbehandlingOppdatertHendelse): Unit? {
        return client.post(
            URI.create("http://localhost:$port/oppdater-tilbakekreving-oppgaver"),
            PostRequest(body = tilbakekrevingsbehandlingOppdatertHendelse)
        )
    }

    private fun tildelOppgaver(oppgaver: List<Long>, ident: String): TildelOppgaveResponse? {
        return client.post(
            URI.create("http://localhost:$port/tildel-oppgaver"),
            PostRequest(
                body = TildelOppgaveRequest(
                    oppgaver = oppgaver,
                    saksbehandlerIdent = ident
                ),
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerOppfolging.id)).token()}")
                )
            )
        )
    }

    private fun søkEtterSaksbehandlere(
        søketekst: String,
        oppgaver: List<Long>,
        enheter: List<String> = emptyList()
    ): SaksbehandlerSøkResponse? {
        return client.post(
            URI.create("http://localhost:$port/saksbehandler-sok"),
            PostRequest(
                body = SaksbehandlerSøkRequest(
                    søketekst = søketekst,
                    oppgaver = oppgaver,
                    enheter = enheter,
                ),
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerOppfolging.id)).token()}")
                )
            )
        )
    }

    private fun plukkOppgave(oppgaveId: OppgaveId): PlukkOppgaveResponse? {
        return client.post(
            URI.create("http://localhost:$port/plukk-oppgave"),
            PostRequest(
                body = PlukkOppgaveRequest(oppgaveId.id, oppgaveId.versjon),
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerOppfolging.id)).token()}")
                )
            )
        )
    }

    private fun avreserverOppgaver(oppgaver: List<Long>): List<OppgaveId>? {
        return client.post(
            URI.create("http://localhost:$port/avreserver-oppgaver"),
            PostRequest(
                body = AvreserverOppgaveDto(oppgaver),
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerOppfolging.id)).token()}")
                )
            )
        )
    }

    private fun hentGjeldendeMarkeringerForBehandling(behandlingRef: UUID): List<MarkeringDto>? {
        return client.get(
            URI.create("http://localhost:$port/$behandlingRef/hent-gjeldende-markeringer-for-behandling"),
            GetRequest()
        )
    }

    private fun opprettMarkeringHendelse(behandlingRef: UUID, markering: MarkeringDto): Unit? {
        return client.post(
            URI.create("http://localhost:$port/$behandlingRef/opprett-markering-hendelse"),
            PostRequest(
                body = markering,
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerOppfolging.id)).token()}")
                )
            )
        )
    }

    private fun hentOppgaveVisningsinfo(referanse: UUID): OppgaveVisningsinformasjonResponse? {
        return oboClient.get(
            URI.create("http://localhost:$port/${referanse}/hent-oppgave-visningsinformasjon"),
            GetRequest(
                currentToken = getOboToken()
            )
        )
    }

    private fun hentOppgaveliste(request: OppgavelisteRequest): OppgavelisteRespons? {
        return oboClient.post(
            URI.create("http://localhost:$port/oppgaveliste"),
            PostRequest(
                body = request,
                currentToken = getOboToken()
            )
        )
    }

    private fun hentMineOppgaver(kunPåVent: Boolean = false): OppgavelisteRespons {
        val s = if (kunPåVent) "?kunPaaVent=true" else ""
        return oboClient.get<OppgavelisteRespons>(
            URI.create("http://localhost:$port/mine-oppgaver$s"),
            GetRequest(currentToken = getOboToken())
        )!!
    }

    private fun søkEtterOppgaver(søkRequest: SøkRequest): SøkResponse? {
        return oboClient.post(
            URI.create("http://localhost:$port/sok"),
            PostRequest(body = søkRequest, currentToken = getOboToken())
        )
    }

    private fun OppgaveVisningsinformasjonResponse.oppgaveId(): OppgaveId = OppgaveId(this.id, this.versjon)

    companion object {
        @JvmStatic
        @Container
        private val postgres = PostgreSQLContainer("postgres:16").waitingFor(HostPortWaitStrategy())
            .withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS))

        private val dbConfig = {
            DbConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password
            )
        }

        private val client = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "oppgave"),
            tokenProvider = AzureM2MTokenProvider
        )

        private val oboClient = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "oppgave"),
            tokenProvider = AzureOBOTokenProvider
        )

        private val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        // Starter server
        private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

        private fun resetDatabase() {
            @Suppress("SqlWithoutWhere")
            dataSource.transaction {
                it.execute("DELETE FROM OPPGAVE_HISTORIKK")
                it.execute("DELETE FROM OPPGAVE")
            }
        }

        private fun leggInnFilterForTest() {
            dataSource.transaction {
                val filterId =
                    it.executeReturnKey("INSERT INTO FILTER (NAVN, BESKRIVELSE, OPPRETTET_AV, OPPRETTET_TIDSPUNKT) VALUES ('Alle oppgaver', 'Alle oppgaver', 'test', current_timestamp)")
                testFilterId = filterId
            }
        }

        private fun hentOppgaveGittOppgaveId(oppgaveId: OppgaveId): Oppgave {
            return dataSource.transaction { connection ->
                OppgaveRepository(connection).hentOppgave(oppgaveId.id)
            }
        }

        private fun hentOppgaveGittBehandlingref(behandlingRef: BehandlingReferanse): Oppgave? {
            return dataSource.transaction { connection ->
                OppgaveRepository(connection).hentAktivOppgave(behandlingRef)
            }
        }

        private fun reserverOppgave(oppgaveId: OppgaveId, ident: String, resevertAvIdent: String) {
            return dataSource.transaction { connection ->
                OppgaveRepository(connection).reserverOppgave(oppgaveId, ident, resevertAvIdent, null)
            }
        }

        private fun settFortroligAdresseForOppgave(oppgaveId: OppgaveId, skalHaFortroligAdresse: Boolean) {
            return dataSource.transaction { connection ->
                OppgaveRepository(connection).settFortroligAdresse(
                    oppgaveId = oppgaveId,
                    harFortroligAdresse = skalHaFortroligAdresse
                )
            }
        }

        private fun oppdaterOgHentOppgave(
            oppgaveId: OppgaveId,
            personIdent: String = "123456721",
            enhet: String = ENHET_NAV_LØRENSKOG,
            påVentTil: LocalDate? = null,
            påVentÅrsak: String? = null,
            påVentBegrunnelse: String? = null,
            oppfølgingsenhet: String? = null,
            veilederArbeid: String? = null,
            veilederSykdom: String? = null,
            vurderingsbehov: List<String> = emptyList(),
            erSkjermet: Boolean = false,
            returInformasjon: ReturInfo? = null,
            utløptVentefrist: LocalDate? = null,
            harFortroligAdresse: Boolean = false,
        ): Oppgave {
            dataSource.transaction { connection ->
                OppgaveRepository(connection).oppdatereOppgave(
                    oppgaveId = oppgaveId,
                    endretAvIdent = "Kelvin",
                    personIdent = personIdent,
                    enhet = enhet,
                    påVentTil = påVentTil,
                    påVentÅrsak = påVentÅrsak,
                    påVentBegrunnelse = påVentBegrunnelse,
                    oppfølgingsenhet = oppfølgingsenhet,
                    veilederArbeid = veilederArbeid,
                    veilederSykdom = veilederSykdom,
                    vurderingsbehov = vurderingsbehov,
                    erSkjermet = erSkjermet,
                    returInformasjon = returInformasjon,
                    utløptVentefrist = utløptVentefrist,
                    harFortroligAdresse = harFortroligAdresse
                )
            }
            return hentOppgaveGittOppgaveId(oppgaveId)
        }

        var port: Int = 0
        private lateinit var dataSource: DataSource

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            postgres.start()
            dataSource = initDatasource(dbConfig(), prometheus)
            server = embeddedServer(Netty, port = 0) {
                server(dbConfig = dbConfig(), prometheus = prometheus)
            }.start()

            port = server.port()
            TilgangGateway.disableCaching()
            leggInnFilterForTest()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            dataSource.connection.close()
            server.stop()
            postgres.close()
        }
    }

}

fun getOboToken(roller: List<String> = emptyList()): OidcToken {
    return OidcToken(AzureTokenGen("behandlingsflyt", "behandlingsflyt").generate(false, roller))
}
