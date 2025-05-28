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
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
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
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.motor.JobbInput
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.fakes.AzureTokenGen
import no.nav.aap.oppgave.fakes.Fakes
import no.nav.aap.oppgave.fakes.FakesConfig
import no.nav.aap.oppgave.fakes.STRENGT_FORTROLIG_IDENT
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.filter.FilterId
import no.nav.aap.oppgave.liste.OppgavelisteRespons
import no.nav.aap.oppgave.plukk.FinnNesteOppgaveDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.plukk.PlukkOppgaveDto
import no.nav.aap.oppgave.produksjonsstyring.AntallOppgaverDto
import no.nav.aap.oppgave.prosessering.OppdaterOppgaveEnhetJobb
import no.nav.aap.oppgave.server.DbConfig
import no.nav.aap.oppgave.server.initDatasource
import no.nav.aap.oppgave.server.server
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.tilgang.SaksbehandlerNasjonal
import no.nav.aap.tilgang.SaksbehandlerOppfolging
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.listOf
import kotlin.test.AfterTest

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

        // Hent oppgaven som ble opprettet
        val oppgave = hentOppgave(saksnummer, referanse, Definisjon.AVKLAR_SYKDOM)
        assertThat(oppgave).isNotNull
        assertThat(oppgave!!.enhet).isEqualTo("superNav!")

        // Plukk neste oppgave
        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave!!.oppgaveId).isEqualTo(oppgave.id)

        // Hent hele oppgaven
        val oppgaven = hentOppgave(OppgaveId(nesteOppgave.oppgaveId, nesteOppgave.oppgaveVersjon))
        assertThat(oppgaven.årsakerTilBehandling).containsExactly("SØKNAD")

        // Sjekk at oppgave kommer i mine oppgaver listen
        assertThat(hentMineOppgaver().oppgaver.first().id).isEqualTo(oppgave.id)

        // Avslutt oppgave
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
                    )
                )
            )
        )

        // Sjekk at det ikke er flere oppgaver i køen
        nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()
    }

    @Test
    fun `hent og oppdater oppgave for NAY`() {
        leggInnFilterForTest()
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

        // Hent oppgaven som ble opprettet
        val oppgave = hentOppgave(saksnummer, referanse, Definisjon.AVKLAR_SAMORDNING_GRADERING)
        assertThat(oppgave).isNotNull
        assertThat(oppgave!!.enhet).isEqualTo("4491")

        // Plukk neste oppgave
        var nesteOppgave = hentNesteOppgaveNAY()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave!!.oppgaveId).isEqualTo(oppgave.id)

        // Sjekk at oppgave kommer i mine oppgaver listen
        assertThat(hentMineOppgaver().oppgaver.first().id).isEqualTo(oppgave.id)

        // Avslutt oppgave
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
                    )
                )
            )
        )

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
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_BISTANDSBEHOV,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                        )
                    ),
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(
                                no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                                endretAv = "Lokalsaksbehandler"
                            )
                        )
                    ),
                )
            )
        )

        // Hent oppgaven som ble opprettet
        val oppgave = hentOppgave(saksnummer, referanse, Definisjon.AVKLAR_BISTANDSBEHOV)
        assertThat(oppgave).isNotNull

        // Sjekk at oppgave kommer i mine oppgaver listen
        assertThat(hentMineOppgaver().oppgaver.first().id).isEqualTo(oppgave!!.id)

        // Avslutt plukket oppgave
        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer, referanse = referanse, behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_BISTANDSBEHOV,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(
                                no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                                endretAv = "Lokalsaksbehandler"
                            ),
                        )
                    ),
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_SYKDOM,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET),
                            Endring(
                                no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET,
                                endretAv = "Lokalsaksbehandler"
                            )
                        )
                    ),
                )
            )
        )

        // Sjekk at det ikke er flere oppgaver i køen
        val nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()
    }

    @Test
    fun `Oppgave skal oppdateres med på vent årsak og dato dersom behandlingen er på vent`() {
        leggInnFilterForTest()

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

        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()

        val påVentOppgaver = hentOppgave(
            saksnummer = saksnummer,
            referanse = referanse,
            definisjon = Definisjon.AVKLAR_SYKDOM
        )!!
        assertThat(påVentOppgaver)
            .extracting(OppgaveDto::påVentÅrsak, OppgaveDto::venteBegrunnelse)
            .containsExactly(ÅrsakTilSettPåVent.VENTER_PÅ_MEDISINSKE_OPPLYSNINGER.name, "Bedre ting å gjøre")

        val uthentetPåVent = hentOppgave(
            OppgaveId(
                påVentOppgaver.id!!,
                påVentOppgaver.versjon
            )
        )
        assertThat(uthentetPåVent)
            .extracting(OppgaveDto::venteBegrunnelse, OppgaveDto::påVentTil, OppgaveDto::påVentÅrsak)
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
        nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
        assertThat(nesteOppgave?.avklaringsbehovReferanse?.referanse).isEqualTo(referanse)

        val uthentet = hentOppgave(
            OppgaveId(
                nesteOppgave!!.oppgaveId,
                versjon = nesteOppgave.oppgaveVersjon
            )
        )
        assertThat(uthentet)
            .extracting(OppgaveDto::venteBegrunnelse, OppgaveDto::påVentTil, OppgaveDto::påVentÅrsak)
            .containsOnlyNulls()

        val påVentOppgaverEtterPå = hentMineOppgaver(kunPåVent = true)
        assertThat(påVentOppgaverEtterPå.oppgaver).isEmpty()
    }

    @Test
    fun `Skal ikke få plukket oppgave dersom tilgang nektes`() {
        leggInnFilterForTest()
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

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        var nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNull()

        fakesConfig.negativtSvarFraTilgangForBehandling = setOf()
        nesteOppgave = hentNesteOppgave()
        assertThat(nesteOppgave).isNotNull()
    }

    @Test
    fun `Oppgave skal avreserveres dersom tilgang nektes`() {
        leggInnFilterForTest()
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
        hentNesteOppgave()
        val reservertOppgaveMedTilgang = hentOppgave(
            saksnummer = saksnummer, referanse = referanse,
            definisjon = Definisjon.AVKLAR_SYKDOM,
        )
        assertThat(reservertOppgaveMedTilgang).isNotNull()
        assertThat(reservertOppgaveMedTilgang?.reservertAv).isNotNull()
        assertThat(reservertOppgaveMedTilgang?.reservertTidspunkt).isNotNull()

        // plukk uten tilgang
        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        assertThatThrownBy {
            plukkOppgave(
                OppgaveId(
                    reservertOppgaveMedTilgang?.id!!,
                    reservertOppgaveMedTilgang.versjon
                )
            )
        }
            .isInstanceOf(ManglerTilgangException::class.java)

        // sjekk at reservasjon er fjernet
        val oppgaveUtenReservasjon =
            hentOppgave(OppgaveId(reservertOppgaveMedTilgang?.id!!, reservertOppgaveMedTilgang.versjon))
        assertThat(oppgaveUtenReservasjon).isNotNull()
        assertThat(oppgaveUtenReservasjon.reservertAv == null)
        assertThat(oppgaveUtenReservasjon.reservertTidspunkt == null)
    }

    @Test
    fun `Oppdaterer enhet på mislykket forsøk på plukk`() {
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


        val oppgaveMedGammelEnhet = hentOppgave(saksnummer, referanse, definisjon = Definisjon.AVKLAR_SYKDOM)
        assertThat(oppgaveMedGammelEnhet).isNotNull()

        // oppdater enhet på oppgave
        val oppgaveMedNyEnhet = oppdaterOgHentOppgave(
            OppgaveDto(
                id = oppgaveMedGammelEnhet!!.id,
                saksnummer = oppgaveMedGammelEnhet.saksnummer,
                behandlingRef = oppgaveMedGammelEnhet.behandlingRef,
                enhet = "nyEnhet",
                oppfølgingsenhet = "nyOppfølgingsenhet",
                veileder = oppgaveMedGammelEnhet.veileder,
                behandlingOpprettet = oppgaveMedGammelEnhet.behandlingOpprettet,
                avklaringsbehovKode = oppgaveMedGammelEnhet.avklaringsbehovKode,
                status = oppgaveMedGammelEnhet.status,
                behandlingstype = oppgaveMedGammelEnhet.behandlingstype,
                opprettetAv = oppgaveMedGammelEnhet.opprettetAv,
                opprettetTidspunkt = oppgaveMedGammelEnhet.opprettetTidspunkt,
                versjon = oppgaveMedGammelEnhet.versjon,
            )
        )
        assertThat(oppgaveMedNyEnhet.enhet).isEqualTo("nyEnhet")
        assertThat(oppgaveMedNyEnhet.oppfølgingsenhet).isEqualTo("nyOppfølgingsenhet")

        // plukk uten tilgang
        fakesConfig.negativtSvarFraTilgangForBehandling = setOf(referanse)
        assertThatThrownBy { plukkOppgave(OppgaveId(oppgaveMedNyEnhet.id!!, oppgaveMedNyEnhet.versjon)) }.isInstanceOf(
            ManglerTilgangException::class.java
        )

        // enhet skal ha blitt oppdatert etter mislykket plukk
        val oppgaveEtterOppdatering = hentOppgave(OppgaveId(oppgaveMedNyEnhet.id!!, oppgaveMedNyEnhet.versjon))
        assertThat(oppgaveEtterOppdatering).isNotNull()
        assertThat(oppgaveEtterOppdatering.enhet).isEqualTo("superNav!")
        assertThat(oppgaveEtterOppdatering.oppfølgingsenhet).isNull()
    }

    @Test
    fun `Kan oppdatere oppgave til fortrolig adresse`() {
        leggInnFilterForTest()
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

        val oppgaveUtenFortroligAdresse = hentOppgave(saksnummer1, referanse1, definisjon = Definisjon.AVKLAR_SYKDOM)
        assertThat(oppgaveUtenFortroligAdresse).isNotNull()

        // sett fortrolig adresse
        settFortroligAdresseForOppgave(
            oppgaveId = OppgaveId(
                oppgaveUtenFortroligAdresse?.id!!, oppgaveUtenFortroligAdresse.versjon
            ), skalHaFortroligAdresse = true
        )

        // hent på nytt
        val oppgaveMedFortroligAdresse = hentOppgave(
            OppgaveId(
                oppgaveUtenFortroligAdresse.id!!, oppgaveUtenFortroligAdresse.versjon
            )
        )
        assertThat(oppgaveMedFortroligAdresse.harFortroligAdresse).isTrue()
    }

    @Test
    fun `Skal forsøke å reservere flere oppgaver dersom bruker ikke har tilgang på den første`() {
        leggInnFilterForTest()
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

        val saksnummer2 = "100003"
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
    fun `Hent antall oppgaver uten oppgitt behandlingstype`() {
        val saksnummer = "100004"
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

        val antallOppgaver = hentAntallOppgaver()
        assertThat(antallOppgaver.keys).hasSize(1)
        assertThat(antallOppgaver[Definisjon.AVKLAR_SYKDOM.kode.name]).isEqualTo(1)
    }

    @Test
    fun `Hent antall oppgaver kun for revurdering`() {
        val saksnummer1 = "100005"
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

        val saksnummer2 = "100006"
        val referanse2 = UUID.randomUUID()

        oppdaterOppgaver(
            opprettBehandlingshistorikk(
                saksnummer = saksnummer2,
                referanse = referanse2,
                typeBehandling = TypeBehandling.Revurdering,
                behandlingsbehov = listOf(
                    Behandlingsbehov(
                        definisjon = Definisjon.AVKLAR_STUDENT,
                        status = no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET,
                        endringer = listOf(
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.OPPRETTET)
                        )
                    )
                )
            )
        )

        val antallOppgaver = hentAntallOppgaver(Behandlingstype.REVURDERING)
        assertThat(antallOppgaver.keys).hasSize(1)
        assertThat(antallOppgaver[Definisjon.AVKLAR_STUDENT.kode.name]).isEqualTo(1)
    }

    @Test
    fun `oppgaver skal merkes med returstatus`() {
        leggInnFilterForTest()

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

        assertThat(hentAntallOppgaver().keys).hasSize(1)

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

        // Verifiser at oppgaven ble løst
        assertThat(hentAntallOppgaver().keys).hasSize(0)

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
                            Endring(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER),
                        )
                    )
                )
            )
        )

        // Oppgaven er gjenopprettet
        assertThat(hentAntallOppgaver().keys).hasSize(1)

        val oppgaven = hentOppgave(saksnummer1, referanse1, Definisjon.AVKLAR_SYKDOM)!!

        assertThat(oppgaven).extracting(OppgaveDto::returStatus).isEqualTo(ReturStatus.RETUR_FRA_KVALITETSSIKRER)
    }

    // TODO: Flytt denne i egen klasse når fakes er skrevet om
    @Test
    fun `Skal avreservere og flytte oppgaver til Vikafossen dersom person har fått strengt fortrolig adresse`() {
        val oppgaveId1 = opprettOppgave(personIdent = STRENGT_FORTROLIG_IDENT)
        val oppgaveId2 = opprettOppgave()
        val oppgave2Før = hentOppgave(oppgaveId2)

        initDatasource(dbConfig, prometheus).transaction {
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

    private fun hentAntallOppgaver(behandlingstype: Behandlingstype? = null): Map<String, Int> {
        return client.post(
            URI.create("http://localhost:8080/produksjonsstyring/antall-oppgaver"),
            PostRequest(body = AntallOppgaverDto(behandlingstype = behandlingstype))
        )!!
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
    )

    private fun opprettBehandlingshistorikk(
        saksnummer: String,
        referanse: UUID,
        behandlingStatus: Status = Status.OPPRETTET,
        behandlingsbehov: List<Behandlingsbehov>,
        typeBehandling: TypeBehandling = TypeBehandling.Førstegangsbehandling
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
                )
            }
            AvklaringsbehovHendelseDto(
                avklaringsbehovDefinisjon = avklaringsbehovHendelse.definisjon,
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
            årsakerTilBehandling = listOf("SØKNAD"),
            erPåVent = avklaringsbehovHendelseDtoListe.any { it.avklaringsbehovDefinisjon.erVentebehov() && it.status != no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET },
        )
    }

    private fun oppdaterOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse): Unit? {
        return client.post(
            URI.create("http://localhost:8080/oppdater-oppgaver"),
            PostRequest(body = behandlingFlytStoppetHendelse)
        )
    }

    private fun plukkOppgave(oppgaveId: OppgaveId): OppgaveDto? {
        return client.post(
            URI.create("http://localhost:8080/plukk-oppgave"),
            PostRequest(
                body = PlukkOppgaveDto(oppgaveId.id, oppgaveId.versjon),
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerOppfolging.id)).token()}")
                )
            )
        )
    }

    private fun hentNesteOppgave(): NesteOppgaveDto? {
        val alleFilter = hentAlleFilter()
        val nesteOppgave: NesteOppgaveDto? = noTokenClient.post(
            URI.create("http://localhost:8080/neste-oppgave"),
            PostRequest(
                body = FinnNesteOppgaveDto(filterId = alleFilter.first { it.navn.contains("Alle") }.id!!),
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerOppfolging.id)).token()}")
                )
            )
        )
        return nesteOppgave
    }

    private fun hentNesteOppgaveNAY(): NesteOppgaveDto? {
        val alleFilter =
            hentAlleFilter().filter { it.avklaringsbehovKoder.isEmpty() }.filter { it.behandlingstyper.isEmpty() }
        val nesteOppgave: NesteOppgaveDto? = noTokenClient.post(
            URI.create("http://localhost:8080/neste-oppgave"),
            PostRequest(
                body = FinnNesteOppgaveDto(filterId = alleFilter.first().id!!),
                additionalHeaders = listOf(
                    Header("Authorization", "Bearer ${getOboToken(listOf(SaksbehandlerNasjonal.id)).token()}")
                )
            )
        )
        return nesteOppgave
    }

    private fun hentOppgave(saksnummer: String, referanse: UUID, definisjon: Definisjon): OppgaveDto? {
        return oboClient.post(
            URI.create("http://localhost:8080/hent-oppgave"),
            PostRequest(
                body = AvklaringsbehovReferanseDto(
                    saksnummer = saksnummer,
                    referanse = referanse,
                    journalpostId = null,
                    avklaringsbehovKode = definisjon.kode.name
                ),
                currentToken = getOboToken()
            )
        )
    }

    private fun hentMineOppgaver(kunPåVent: Boolean = false): OppgavelisteRespons {
        val s = if (kunPåVent) "?kunPaaVent=true" else ""
        return oboClient.get<OppgavelisteRespons>(
            URI.create("http://localhost:8080/mine-oppgaver$s"),
            GetRequest(currentToken = getOboToken())
        )!!
    }

    private fun opprettEllerOppdaterFilter(filter: FilterDto): FilterDto? {
        return oboClient.post(
            URI.create("http://localhost:8080/filter"),
            PostRequest(body = filter, currentToken = getOboToken())
        )
    }

    private fun hentAlleFilter(): List<FilterDto> {
        return oboClient.get<List<FilterDto>>(
            URI.create("http://localhost:8080/filter"),
            GetRequest(currentToken = getOboToken())
        )!!
    }

    private fun hentFilter(enheter: List<String>): List<FilterDto> {
        return oboClient.get<List<FilterDto>>(
            URI.create("http://localhost:8080/filter?enheter=${enheter.joinToString("&enheter=")}"),
            GetRequest(currentToken = getOboToken())
        )!!
    }

    private fun slettFilter(filterId: FilterId): Unit? {
        return oboClient.post(
            URI.create("http://localhost:8080/filter/${filterId.filterId}/slett"),
            PostRequest(body = filterId, currentToken = getOboToken())
        )
    }

    companion object {
        private val postgres = postgreSQLContainer()
        val fakesConfig: FakesConfig = FakesConfig()
        private val fakes = Fakes(fakesConfig = fakesConfig)

        private val dbConfig = DbConfig(
            jdbcUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )

        private val client = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "oppgave"),
            tokenProvider = ClientCredentialsTokenProvider
        )

        private val oboClient = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "oppgave"),
            tokenProvider = OnBehalfOfTokenProvider
        )
        private val noTokenClient = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "oppgave"),
            tokenProvider = NoTokenTokenProvider()
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
                it.execute("DELETE FROM FILTER_ENHET")
                it.execute("DELETE FROM FILTER")
            }
        }

        private fun leggInnFilterForTest() {
            initDatasource(dbConfig, prometheus).transaction {
                val filterId =
                    it.executeReturnKey("INSERT INTO FILTER (NAVN, BESKRIVELSE, OPPRETTET_AV, OPPRETTET_TIDSPUNKT) VALUES ('Alle oppgaver', 'Alle oppgaver', 'test', current_timestamp)")
                it.execute("INSERT INTO FILTER_ENHET (FILTER_ID, ENHET) VALUES (?, ?)") {
                    setParams {
                        setLong(1, filterId)
                        setString(2, "ALLE")
                    }
                }
            }
        }

        private fun hentOppgave(oppgaveId: OppgaveId): OppgaveDto {
            return initDatasource(dbConfig, prometheus).transaction { connection ->
                OppgaveRepository(connection).hentOppgave(oppgaveId)
            }
        }

        private fun settFortroligAdresseForOppgave(oppgaveId: OppgaveId, skalHaFortroligAdresse: Boolean) {
            return initDatasource(dbConfig, prometheus).transaction { connection ->
                OppgaveRepository(connection).settFortroligAdresse(
                    oppgaveId = oppgaveId,
                    harFortroligAdresse = skalHaFortroligAdresse
                )
            }
        }

        private fun oppdaterOgHentOppgave(oppgave: OppgaveDto): OppgaveDto {
            initDatasource(dbConfig, prometheus).transaction { connection ->
                OppgaveRepository(connection).oppdatereOppgave(
                    oppgaveId = OppgaveId(oppgave.id!!, oppgave.versjon),
                    ident = "Kelvin",
                    personIdent = oppgave.personIdent,
                    enhet = oppgave.enhet,
                    påVentTil = oppgave.påVentTil,
                    påVentÅrsak = oppgave.påVentÅrsak,
                    påVentBegrunnelse = oppgave.venteBegrunnelse,
                    oppfølgingsenhet = oppgave.oppfølgingsenhet,
                    veileder = oppgave.veileder,
                    årsakerTilBehandling = oppgave.årsakerTilBehandling,
                    returStatus = oppgave.returStatus
                )
            }
            return hentOppgave(OppgaveId(oppgave.id!!, oppgave.versjon))
        }

        private fun opprettOppgave(
            personIdent: String = "12345678901",
            saksnummer: String = "123",
            behandlingRef: UUID = UUID.randomUUID(),
            status: no.nav.aap.oppgave.verdityper.Status = no.nav.aap.oppgave.verdityper.Status.OPPRETTET,
            avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
            behandlingstype: Behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
            enhet: String = "0230",
            oppfølgingsenhet: String? = null,
            veileder: String? = null,
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
                veileder = veileder,
                opprettetTidspunkt = LocalDateTime.now()
            )
            return initDatasource(dbConfig, prometheus).transaction { connection ->
                OppgaveRepository(connection).opprettOppgave(oppgaveDto)
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

private fun getOboToken(roller: List<String> = emptyList()): OidcToken {
    return OidcToken(AzureTokenGen("behandlingsflyt", "behandlingsflyt").generate(false, roller))
}