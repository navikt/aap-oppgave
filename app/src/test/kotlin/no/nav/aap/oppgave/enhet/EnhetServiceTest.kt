package no.nav.aap.oppgave.enhet

import io.getunleash.FakeUnleash
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.VURDER_KLAGE_KONTOR_KODE
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.fakes.AzureTokenGen
import no.nav.aap.oppgave.klienter.arena.IVeilarbarenaGateway
import no.nav.aap.oppgave.klienter.msgraph.Group
import no.nav.aap.oppgave.klienter.msgraph.GroupMembers
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.klienter.msgraph.MemberOf
import no.nav.aap.oppgave.klienter.nom.skjerming.SkjermingGateway
import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.Code
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytning
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytningType
import no.nav.aap.oppgave.klienter.pdl.Gradering
import no.nav.aap.oppgave.klienter.pdl.HentPersonBolkResult
import no.nav.aap.oppgave.klienter.pdl.HentPersonResult
import no.nav.aap.oppgave.klienter.pdl.IPdlGateway
import no.nav.aap.oppgave.klienter.pdl.PdlData
import no.nav.aap.oppgave.klienter.pdl.PdlPerson
import no.nav.aap.oppgave.unleash.UnleashService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

private val IDENT_MED_SKJERMING = "12312312312"

class EnhetServiceTest {

    private val NAY_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5098")
    private val VEILEDER_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5006")
    private val KVALITETSSIKRER_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5097")

    @Test
    fun `lister kun opp enhets-roller`() {
        val service = EnhetService(graphGateway, PdlGatewayMock(), nomGateway, NorgGatewayMock(), VeilarbarenaGatewayMock(),
            UnleashService(FakeUnleash().apply {
                enableAll()
            }),)

        val res = service.hentEnheter(
            "xxx",
            OidcToken(AzureTokenGen("behandlingsflyt", "behandlingsflyt").generate(false, emptyList()))
        )
        assertThat(res).isNotEmpty()
        assertThat(res).hasSize(1)
        assertThat(res[0]).isEqualTo("12345")

    }

    @Test
    fun `Skal utlede riktig enhet basert på avklaringsbehovkode`() {
        val nomGateway = NomGatewayMock.medRespons(erEgenansatt = false)
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0400"))
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), unleashService = UnleashService(FakeUnleash().apply {
            enableAll()
        }),)

        val utledetEnhetFylke = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList(), erFørstegangsbehandling = false)
        assertThat(utledetEnhetFylke).isNotNull()
        assertThat(utledetEnhetFylke.enhet).isEqualTo("0400")

        val utledetEnhetLokal = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "12345678910", emptyList(), erFørstegangsbehandling = false)
        assertThat(utledetEnhetLokal).isNotNull()
        assertThat(utledetEnhetLokal.enhet).isEqualTo("0403")

        val utledetEnhetNay = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "12345678910", emptyList(), erFørstegangsbehandling = false)
        assertThat(utledetEnhetNay).isNotNull()
        assertThat(utledetEnhetNay.enhet).isEqualTo(Enhet.NAY.kode)

    }

    @Test
    fun `Skal kunne hente fylkeskontor for enhet`() {
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0400"))
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }),)

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList(), erFørstegangsbehandling = false)
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0400")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)

    }
    
    @Test
    fun `Oppfølgingsenhet skal ikke overstyre hvis nasjonal oppfølgingsenhet`() {
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = ("0403"))
        
        val veilarbarenaGateway = VeilarbarenaGatewayMock.medRespons("4154")
        
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway,veilarbarenaGateway, UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "12345678910", emptyList(), erFørstegangsbehandling = true)
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0403")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal ikke prøve å omgjøre til fylkesenhet for vikafossen`() {
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = (Enhet.NAV_VIKAFOSSEN.kode))
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911", emptyList(), erFørstegangsbehandling = true)
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo(
            Enhet.NAV_VIKAFOSSEN.kode
        )
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal bruke oppfølgingsenhetens sin overordnet enhet for kvalitetssikring`() {
        val enhet = "0123"
        val overordnetEnhet = "0100"
        val oppfolgingsenhet = "0212"
        val overordnetOppfolgingsenhet = "0200"

        val veilarbarenaGateway = VeilarbarenaGatewayMock.medRespons(oppfolgingsenhet)
        val norgGateway = NorgGatewayMock.medRespons(
            responsEnhet = enhet,
            enhetTilOverordnetEnhetMap = mapOf(
                enhet to listOf(overordnetEnhet),
                oppfolgingsenhet to listOf(overordnetOppfolgingsenhet)
            )
        )

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, veilarbarenaGateway, UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911", emptyList(), erFørstegangsbehandling = true)
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo(overordnetEnhet)
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(overordnetOppfolgingsenhet)
    }

    @Test
    fun `Skal bruke fylkesenhet med like 2 første siffer om NORG2 returnerer mer enn 1 overordnet fylkesenhet`() {
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0300", "0400"))
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList(), erFørstegangsbehandling = true)
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0400")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal bruke den første fylkesenheten om NORG2 returnerer mer enn 1 overordnet fylkesenhet ingen starter med samme siffer`() {
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0200", "0500"))
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList(), erFørstegangsbehandling = true)
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0200")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal ikke prøve å omgjøre til fylkesenhet for egne ansatte-enheter`() {
        val egneAnsatteOslo = "0383"
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = (egneAnsatteOslo))
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911", emptyList(), erFørstegangsbehandling = true)
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo(
            egneAnsatteOslo
        )
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal returnere NAY-kontor for egen ansatt dersom egen ansatt`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val nomGateway = NomGatewayMock.medRespons(erEgenansatt = true)

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, NorgGatewayMock(), VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.enhet).isEqualTo(Enhet.NAY_EGNE_ANSATTE.kode)
    }

    @Test
    fun `Skal returnere lokal-kontor for egen ansatt dersom egen ansatt`() {
        val egneAnsatteOslo = "0383"
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = (egneAnsatteOslo))
        val nomGateway = NomGatewayMock.medRespons(erEgenansatt = true)


        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.enhet).isEqualTo(egneAnsatteOslo)
    }

    @Test
    fun `Skal ikke sette oppfølgingsenhet for egen ansatt`() {
        val egneAnsatteOslo = "0383"
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = (egneAnsatteOslo))
        val nomGateway = NomGatewayMock.medRespons(erEgenansatt = true)
        val veilarbarenaGateway = VeilarbarenaGatewayMock(oppfølgingsenhet = "1111")

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, veilarbarenaGateway, UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.enhet).isEqualTo(egneAnsatteOslo)
        assertThat(res.oppfølgingsenhet).isNull()

        val res2 = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res2.enhet).isEqualTo(egneAnsatteOslo)
        assertThat(res2.oppfølgingsenhet).isNull()
    }

    @Test
    fun `Vikafossen skal overstyre egne ansatte`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.STRENGT_FORTROLIG)))
            )
        )
        val nomGateway = NomGatewayMock.medRespons(erEgenansatt = true)

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, NorgGatewayMock(), VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.enhet).isEqualTo(Enhet.NAV_VIKAFOSSEN.kode)
    }

    @Test
    fun `Skal sette veileders enhet til NAV_UTLAND for saker knyttet til brukere fra Danmark`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(
                    gtType = GeografiskTilknytningType.UTLAND,
                    gtLand = "DNK"
                )
            )
        )

        val norgGateway = NorgGatewayMock.medRespons()

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)

        assertThat(res.enhet).isEqualTo(Enhet.NAV_UTLAND.kode)
    }

    @Test
    fun `Skal sette kvalitetssikrers enhet til NAV_UTLAND for saker knyttet til brukere fra Danmark`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(
                    gtType = GeografiskTilknytningType.UTLAND,
                    gtLand = "DNK"
                )
            )
        )

        val norgGateway = NorgGatewayMock.medRespons()

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)

        assertThat(res.enhet).isEqualTo(Enhet.NAV_UTLAND.kode)
    }

    @Test
    fun `Strengt fortrolig prioriteres over utland`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.STRENGT_FORTROLIG))),
                hentGeografiskTilknytning = GeografiskTilknytning(
                    gtType = GeografiskTilknytningType.UTLAND,
                    gtLand = "DNK"
                )
            )
        )

        val norgGateway = NorgGatewayMock.medRespons()
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.enhet).isEqualTo(Enhet.NAV_VIKAFOSSEN.kode)
    }

    @Test
    fun `Riktig prioritering av graderinger`() {
        // Strengt fortrolig > Fortrolig > Any
        val strengtFortrolig = listOf(Diskresjonskode.SPSF, Diskresjonskode.SPFO, Diskresjonskode.ANY)
        assertThat(strengtFortrolig.max()).isEqualTo(Diskresjonskode.SPSF)

        // Fortrolig > Any
        val fortrolig = listOf(Diskresjonskode.SPFO, Diskresjonskode.ANY)
        assertThat(fortrolig.max()).isEqualTo(Diskresjonskode.SPFO)

        val ingenGradering = listOf(Diskresjonskode.ANY)
        assertThat(ingenGradering.max()).isEqualTo(Diskresjonskode.ANY)
    }

    @Test
    fun `Skal sette NAYs enhet til NAY_UTLAND for saker knyttet til brukere fra Danmark`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(
                    gtType = GeografiskTilknytningType.UTLAND,
                    gtLand = "DNK"
                )
            )
        )

        val norgGateway = NorgGatewayMock.medRespons()

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)

        assertThat(res.enhet).isEqualTo(Enhet.NAY_UTLAND.kode)
    }

    @Test
    fun `Skal rutes til NAY Utland når oppfølgingskontor er NAV Utland`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val veilarbarenaGateway = VeilarbarenaGatewayMock(oppfølgingsenhet = Enhet.NAV_UTLAND.kode)
        val norgGateway = NorgGatewayMock.medRespons()
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, veilarbarenaGateway, UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.enhet).isEqualTo(Enhet.NAY_UTLAND.kode)
    }

    @Test
    fun `Skal kvalitetssikres av NAY Utland når oppfølgingskontor er NAV Utland`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val veilarbarenaGateway = VeilarbarenaGatewayMock(oppfølgingsenhet = Enhet.NAV_UTLAND.kode)
        val norgGateway = NorgGatewayMock.medRespons(overordnetFylkesEnheter = listOf("0600"), responsEnhet = "0621")
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, veilarbarenaGateway, UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.oppfølgingsenhet).isEqualTo(Enhet.NAV_UTLAND.kode)
    }

    @Test
    fun `Førstegangsbehandling skal gå til Nav Sunnfjord når enhet utledes til en av enhetene i Sunnfjord-regionen`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(GeografiskTilknytningType.KOMMUNE, gtKommune = "any")
            )
        )
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = Enhet.NAV_KINN.kode)
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = true)
        assertThat(res.enhet).isEqualTo(Enhet.NAV_REGION_SUNNFJORD.kode)
    }

    @Test
    fun `Revurdering skal ikke gå til Nav Sunnfjord når enhet utledes til en av enhetene i Sunnfjord-regionen`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(GeografiskTilknytningType.KOMMUNE, gtKommune = "any")
            )
        )
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = Enhet.NAV_KINN.kode)
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList(), erFørstegangsbehandling = false)
        assertThat(res.enhet).isEqualTo(Enhet.NAV_KINN.kode)
    }

    @Test
    fun `Klageoppgave skal gå til Nav Sunnfjord når enhet utledes til en enhet i Sunnfjord-regionen`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(GeografiskTilknytningType.KOMMUNE, gtKommune = "any")
            )
        )
        val norgGateway = NorgGatewayMock.medRespons(responsEnhet = Enhet.NAV_KINN.kode, overordnetFylkesEnheter = listOf("1400"))
        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(AvklaringsbehovKode(VURDER_KLAGE_KONTOR_KODE), "any", emptyList(), erFørstegangsbehandling = false)
        assertThat(res.enhet).isEqualTo(Enhet.NAV_REGION_SUNNFJORD.kode)
    }

    @Test
    fun `Skal sette adressebeskyttelse når relaterte identer har adressebeskyttelse`() {
        val pdlGateway = PdlGatewayMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                // relatert ident har strengt fortrolig adresse i PDL
                hentPersonBolk = listOf(
                    HentPersonBolkResult(
                    ident = "barn",
                    person = PdlPerson(
                        adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.STRENGT_FORTROLIG)),
                        code = Code.ok,
                        navn = emptyList(),
                    ),
                    code = Code.ok.name
                ),
                    HentPersonBolkResult(
                        ident = "barn2",
                        person = PdlPerson(
                            adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.FORTROLIG)),
                            code = Code.ok,
                            navn = emptyList(),
                        ),
                        code = Code.ok.name
                    )    )
            )
        )

        val norgGateway = NorgGatewayMock.medRespons()

        val service = EnhetService(graphGateway, pdlGateway, nomGateway, norgGateway, VeilarbarenaGatewayMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", listOf("barn"), erFørstegangsbehandling = false)

        assertThat(res.enhet).isEqualTo(Enhet.NAV_VIKAFOSSEN.kode)
    }

    @Test
    fun `Behandling av klage på lokalkontor skal rutes til enten fylkeskontor eller lokalkontor`() {
        val norgGatewayVestViken = NorgGatewayMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0600"))

        val serviceVestViken = EnhetService(graphGateway, pdlGateway, nomGateway, norgGatewayVestViken, VeilarbarenaGatewayMock(), unleashService = UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val enhetForKlageOppgave = serviceVestViken.utledEnhetForOppgave(AvklaringsbehovKode(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.VURDER_KLAGE_KONTOR.kode.name), "any", emptyList(), "1234567", erFørstegangsbehandling = false)
        assertThat(enhetForKlageOppgave.enhet).isEqualTo("0600")

        val enhetForVanligOppgave = serviceVestViken.utledEnhetForOppgave(AvklaringsbehovKode(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.AVKLAR_SYKDOM.kode.name), "any", emptyList(), "1234567", erFørstegangsbehandling = false)
        assertThat(enhetForVanligOppgave.enhet).isEqualTo("0403")

        // Når oppgaven ikke skal til fylkeskontor
        val norgGatewayInnlandet = NorgGatewayMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0400"))
        val serviceInnlandet = EnhetService(graphGateway, pdlGateway, nomGateway, norgGatewayInnlandet, VeilarbarenaGatewayMock(), unleashService = UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val enhetForKlageOppgaveInnlandet = serviceInnlandet.utledEnhetForOppgave(AvklaringsbehovKode(no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.VURDER_KLAGE_KONTOR.kode.name), "any", emptyList(), "1234567", erFørstegangsbehandling = false)
        assertThat(enhetForKlageOppgaveInnlandet.enhet).isEqualTo("0403")
    }

    companion object {
        val graphGateway = object : IMsGraphGateway {
            override fun hentEnhetsgrupper(ident: String, currentToken: OidcToken): MemberOf {
                return MemberOf(
                    groups = listOf(
                        Group(name = "0000-GA-ENHET_12345", id = UUID.randomUUID()),
                    )
                )
            }

            override fun hentFortroligAdresseGruppe(ident: String, currentToken: OidcToken): MemberOf {
                return MemberOf()
            }

            override fun hentMedlemmerIGruppe(enhetsnummer: String): GroupMembers {
                return GroupMembers()
            }
        }

        val nomGateway = object : SkjermingGateway {
            override fun erSkjermet(ident: String): Boolean {
                return ident == IDENT_MED_SKJERMING
            }
        }

        val pdlGateway = object : IPdlGateway {
            override fun hentAdressebeskyttelseOgGeolokasjon(personident: String, currentToken: OidcToken?): PdlData {
                return PdlData(
                    hentGeografiskTilknytning = GeografiskTilknytning(
                        gtType = GeografiskTilknytningType.KOMMUNE,
                        gtKommune = "samme det"
                    )
                )
            }

            override fun hentPersoninfoForIdenter(identer: List<String>): PdlData {
                TODO("Not yet implemented")
            }

            override fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData {
                return PdlData(
                    hentPersonBolk = emptyList(),
                )
            }
        }

        class NomGatewayMock(val erEgenansatt: Boolean?) : SkjermingGateway {
            companion object {
                fun medRespons(erEgenansatt: Boolean): NomGatewayMock {
                    return NomGatewayMock(erEgenansatt)
                }
            }

            override fun erSkjermet(ident: String): Boolean {
                return erEgenansatt ?: TODO("Not yet implemented")
            }
        }

        class PdlGatewayMock(val pdlDataRespons: PdlData? = null) : IPdlGateway {
            companion object {
                fun medRespons(pdlDataRespons: PdlData): PdlGatewayMock {
                    return PdlGatewayMock(pdlDataRespons)
                }
            }

            override fun hentAdressebeskyttelseOgGeolokasjon(personident: String, currentToken: OidcToken?): PdlData {
                return pdlDataRespons ?: TODO("Not yet implemented")
            }

            override fun hentPersoninfoForIdenter(identer: List<String>): PdlData {
                return pdlDataRespons ?: TODO("Not yet implemented")
            }

            override fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData {
                return pdlDataRespons ?: TODO("Not yet implemented")
            }
        }

        class NorgGatewayMock(
            val responsEnhet: String? = null,
            val enhetsNavnRespons: Map<String, String>? = null,
            val overordnetFylkesEnheter: List<String>? = null,
            val enhetTilOverordnetEnhetMap: Map<String, List<String>>? = null,
        ) : INorgGateway {
            companion object {
                fun medRespons(
                    responsEnhet: String? = null,
                    enhetsNavnRespons: Map<String, String>? = null,
                    overordnetFylkesEnheter: List<String>? = null,
                    enhetTilOverordnetEnhetMap: Map<String, List<String>>? = null
                ): NorgGatewayMock {
                    return NorgGatewayMock(
                        responsEnhet,
                        enhetsNavnRespons,
                        overordnetFylkesEnheter,
                        enhetTilOverordnetEnhetMap
                    )
                }
            }

            override fun finnEnhet(
                geografiskTilknyttning: String?,
                erNavansatt: Boolean,
                diskresjonskode: Diskresjonskode
            ): String {
                return responsEnhet ?: TODO("Not yet implemented")
            }

            override fun hentEnheter(): Map<String, String> {
                return enhetsNavnRespons ?: TODO("Not yet implemented")
            }

            override fun hentOverordnetFylkesenheter(enhetsnummer: String): List<String> {
                return enhetTilOverordnetEnhetMap?.get(enhetsnummer)
                    ?: overordnetFylkesEnheter
                    ?: TODO("Not yet implemented")
            }
        }

        class VeilarbarenaGatewayMock(
            val oppfølgingsenhet: String? = null
        ) : IVeilarbarenaGateway {
            companion object {
                fun medRespons(oppfølgingsenhet: String? = null): VeilarbarenaGatewayMock {
                    return VeilarbarenaGatewayMock(oppfølgingsenhet)
                }
            }

            override fun hentOppfølgingsenhet(personIdent: String): String? {
                return oppfølgingsenhet
            }
        }
    }
}