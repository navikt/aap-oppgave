package no.nav.aap.oppgave.enhet

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.klienter.arena.IVeilarbarenaClient
import no.nav.aap.oppgave.klienter.msgraph.Group
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.msgraph.MemberOf
import no.nav.aap.oppgave.klienter.nom.INomKlient
import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.oppgave.klienter.norg.INorgKlient
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytning
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytningType
import no.nav.aap.oppgave.klienter.pdl.Gradering
import no.nav.aap.oppgave.klienter.pdl.HentPersonBolkResult
import no.nav.aap.oppgave.klienter.pdl.HentPersonResult
import no.nav.aap.oppgave.klienter.pdl.IPdlKlient
import no.nav.aap.oppgave.klienter.pdl.PdlData
import no.nav.aap.oppgave.unleash.FeatureToggle
import no.nav.aap.oppgave.unleash.IUnleashService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class EnhetServiceTest {

    private val NAY_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5098")
    private val VEILEDER_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5006")
    private val KVALITETSSIKRER_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5097")

    @Test
    fun `lister kun opp enhets-roller`() {
        val service = EnhetService(graphClient, PdlKlientMock(), nomKlient, NorgKlientMock(), veilarbarenaKlient, unleashServiceMock)

        val res = service.hentEnheter("xxx", "")
        assertThat(res).isNotEmpty()
        assertThat(res).hasSize(1)
        assertThat(res[0]).isEqualTo("12345")

    }

    @Test
    fun `Skal utlede riktig enhet basert på avklaringsbehovkode`() {
        val nomKlient = NomKlientMock.medRespons(erEgenansatt = false)
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnhet = "0400")
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, veilarbarenaKlient, unleashServiceMock)

        val utledetEnhetFylke = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910")
        assertThat(utledetEnhetFylke).isNotNull()
        assertThat(utledetEnhetFylke.enhet).isEqualTo("0400")

        val utledetEnhetLokal = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "12345678910")
        assertThat(utledetEnhetLokal).isNotNull()
        assertThat(utledetEnhetLokal.enhet).isEqualTo("0403")

        val utledetEnhetNay = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "12345678910")
        assertThat(utledetEnhetNay).isNotNull()
        assertThat(utledetEnhetNay.enhet).isEqualTo(Enhet.NAY.kode)

    }

    @Test
    fun `Skal kunne hente fylkeskontor for enhet`() {
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnhet = "0400")
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, veilarbarenaKlient, unleashServiceMock)

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910")
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0400")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)

    }

    @Test
    fun `Skal ikke prøve å omgjøre til fylkesenhet for vikafossen`() {
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = (Enhet.NAV_VIKAFOSSEN.kode))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, veilarbarenaKlient, unleashServiceMock)
        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911")
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo(
            Enhet.NAV_VIKAFOSSEN.kode
        )
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal ikke prøve å omgjøre til fylkesenhet for egne ansatte-enheter`() {
        val egneAnsatteOslo = "0383"
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = (egneAnsatteOslo))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, veilarbarenaKlient, unleashServiceMock)

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911")
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo(
            egneAnsatteOslo
        )
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal returnere NAY-kontor for egen ansatt dersom egen ansatt`() {
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val nomKlient = NomKlientMock.medRespons(erEgenansatt = true)

        val service = EnhetService(graphClient, pdlKlient, nomKlient, NorgKlientMock(), veilarbarenaKlient, unleashServiceMock)
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any")
        assertThat(res.enhet).isEqualTo(Enhet.NAY_EGNE_ANSATTE.kode)
    }

    @Test
    fun `Skal returnere lokal-kontor for egen ansatt dersom egen ansatt`() {
        val egneAnsatteOslo = "0383"
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = (egneAnsatteOslo))
        val nomKlient = NomKlientMock.medRespons(erEgenansatt = true)


        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, veilarbarenaKlient, unleashServiceMock)
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any")
        assertThat(res.enhet).isEqualTo(egneAnsatteOslo)
    }

    @Test
    fun `Vikafossen skal overstyre egne ansatte`() {
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.STRENGT_FORTROLIG)))
            )
        )
        val nomKlient = NomKlientMock.medRespons(erEgenansatt = true)

        val service = EnhetService(graphClient, pdlKlient, nomKlient, NorgKlientMock(), veilarbarenaKlient, unleashServiceMock)
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any")
        assertThat(res.enhet).isEqualTo(Enhet.NAV_VIKAFOSSEN.kode)
    }

    companion object {
        val graphClient = object : IMsGraphClient {
            override fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf {
                return MemberOf(
                    groups = listOf(
                        Group(name = "0000-GA-ENHET_12345", id = UUID.randomUUID()),
                    )
                )
            }

        }

        val nomKlient = object : INomKlient {
            override fun erEgenansatt(personident: String): Boolean {
                return false
            }
        }

        val pdlKlient = object : IPdlKlient {
            override fun hentAdressebeskyttelseOgGeolokasjon(personident: String, currentToken: OidcToken?): PdlData {
                return PdlData(
                    hentGeografiskTilknytning = GeografiskTilknytning(
                        gtType = GeografiskTilknytningType.KOMMUNE,
                        gtKommune = "samme det"
                    )
                )
            }

            override fun hentPersoninfoForIdenter(identer: List<String>): PdlData? {
                TODO("Not yet implemented")
            }

            override fun hentAdressebeskyttelseForIdenter(identer: List<String>): List<HentPersonBolkResult> {
                TODO("Not yet implemented")
            }
        }

        class NomKlientMock(val erEgenansatt: Boolean?) : INomKlient {
            companion object {
                fun medRespons(erEgenansatt: Boolean): NomKlientMock {
                    return NomKlientMock(erEgenansatt)
                }
            }

            override fun erEgenansatt(personident: String): Boolean {
                return erEgenansatt ?: TODO("Not yet implemented")
            }
        }

        class PdlKlientMock(val pdlDataRespons: PdlData? = null) : IPdlKlient {
            companion object {
                fun medRespons(pdlDataRespons: PdlData): PdlKlientMock {
                    return PdlKlientMock(pdlDataRespons)
                }
            }

            override fun hentAdressebeskyttelseOgGeolokasjon(personident: String, currentToken: OidcToken?): PdlData {
                return pdlDataRespons ?: TODO("Not yet implemented")
            }

            override fun hentPersoninfoForIdenter(identer: List<String>): PdlData? {
                return pdlDataRespons ?: TODO("Not yet implemented")
            }

            override fun hentAdressebeskyttelseForIdenter(identer: List<String>): List<HentPersonBolkResult> {
                TODO("Not yet implemented")
            }
        }

        class NorgKlientMock(
            val responsEnhet: String? = null,
            val enhetsNavnRespons: Map<String, String>? = null,
            val overordnetFylkesEnhet: String? = null,
        ) : INorgKlient {
            companion object {
                fun medRespons(
                    responsEnhet: String? = null,
                    enhetsNavnRespons: Map<String, String>? = null,
                    overordnetFylkesEnhet: String? = null,
                ): NorgKlientMock {
                    return NorgKlientMock(responsEnhet, enhetsNavnRespons, overordnetFylkesEnhet)
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

            override fun hentOverordnetFylkesenhet(enhetsnummer: String): String {
                return overordnetFylkesEnhet ?: TODO("Not yet implemented")
            }
        }

        val veilarbarenaKlient = object : IVeilarbarenaClient {
            override fun hentOppfølgingsenhet(personIdent: String): String? {
                return null
            }
        }

        val unleashServiceMock = object : IUnleashService {
            override fun isEnabled(featureToggle: FeatureToggle): Boolean {
                return true
            }
        }
    }
}