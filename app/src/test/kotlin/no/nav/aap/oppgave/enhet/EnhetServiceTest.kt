package no.nav.aap.oppgave.enhet

import io.getunleash.FakeUnleash
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.klienter.arena.IVeilarbarenaClient
import no.nav.aap.oppgave.klienter.msgraph.Group
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.msgraph.MemberOf
import no.nav.aap.oppgave.klienter.nom.skjerming.SkjermingKlient
import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.oppgave.klienter.norg.INorgKlient
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.Code
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytning
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytningType
import no.nav.aap.oppgave.klienter.pdl.Gradering
import no.nav.aap.oppgave.klienter.pdl.HentPersonBolkResult
import no.nav.aap.oppgave.klienter.pdl.HentPersonResult
import no.nav.aap.oppgave.klienter.pdl.IPdlKlient
import no.nav.aap.oppgave.klienter.pdl.PdlData
import no.nav.aap.oppgave.klienter.pdl.PdlPerson
import no.nav.aap.oppgave.unleash.UnleashService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class EnhetServiceTest {

    private val NAY_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5098")
    private val VEILEDER_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5006")
    private val KVALITETSSIKRER_AVKLARINGSBEHOVKODE = AvklaringsbehovKode("5097")

    @Test
    fun `lister kun opp enhets-roller`() {
        val service = EnhetService(graphClient, PdlKlientMock(), nomKlient, NorgKlientMock(), VeilarbarenaKlientMock(),
            UnleashService(FakeUnleash().apply {
                enableAll()
            }),)

        val res = service.hentEnheter("xxx", "")
        assertThat(res).isNotEmpty()
        assertThat(res).hasSize(1)
        assertThat(res[0]).isEqualTo("12345")

    }

    @Test
    fun `Skal utlede riktig enhet basert på avklaringsbehovkode`() {
        val nomKlient = NomKlientMock.medRespons(erEgenansatt = false)
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0400"))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), unleashService = UnleashService(FakeUnleash().apply {
            enableAll()
        }),)

        val utledetEnhetFylke = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList())
        assertThat(utledetEnhetFylke).isNotNull()
        assertThat(utledetEnhetFylke.enhet).isEqualTo("0400")

        val utledetEnhetLokal = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "12345678910", emptyList())
        assertThat(utledetEnhetLokal).isNotNull()
        assertThat(utledetEnhetLokal.enhet).isEqualTo("0403")

        val utledetEnhetNay = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "12345678910", emptyList())
        assertThat(utledetEnhetNay).isNotNull()
        assertThat(utledetEnhetNay.enhet).isEqualTo(Enhet.NAY.kode)

    }

    @Test
    fun `Skal kunne hente fylkeskontor for enhet`() {
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0400"))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }),)

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList())
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0400")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)

    }
    
    @Test
    fun `Oppfølgingsenhet skal ikke overstyre hvis nasjonal oppfølgingsenhet`() {
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = ("0403"))
        
        val veilarbarenaClient = VeilarbarenaKlientMock.medRespons("4154")
        
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient,veilarbarenaClient, UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "12345678910", emptyList())
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0403")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal ikke prøve å omgjøre til fylkesenhet for vikafossen`() {
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = (Enhet.NAV_VIKAFOSSEN.kode))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911", emptyList())
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

        val veilarbarenaClient = VeilarbarenaKlientMock.medRespons(oppfolgingsenhet)
        val norgKlient = NorgKlientMock.medRespons(
            responsEnhet = enhet,
            enhetTilOverordnetEnhetMap = mapOf(
                enhet to listOf(overordnetEnhet),
                oppfolgingsenhet to listOf(overordnetOppfolgingsenhet)
            )
        )

        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, veilarbarenaClient, UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911", emptyList())
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo(overordnetEnhet)
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(overordnetOppfolgingsenhet)
    }

    @Test
    fun `Skal bruke fylkesenhet med like 2 første siffer om NORG2 returnerer mer enn 1 overordnet fylkesenhet`() {
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0300", "0400"))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList())
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0400")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal bruke den første fylkesenheten om NORG2 returnerer mer enn 1 overordnet fylkesenhet ingen starter med samme siffer`() {
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = ("0403"), overordnetFylkesEnheter = listOf("0200", "0500"))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678910", emptyList())
        assertThat(utledetEnhet).isNotNull()
        assertThat(utledetEnhet.enhet).isEqualTo("0200")
        assertThat(utledetEnhet.oppfølgingsenhet).isEqualTo(null)
    }

    @Test
    fun `Skal ikke prøve å omgjøre til fylkesenhet for egne ansatte-enheter`() {
        val egneAnsatteOslo = "0383"
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = (egneAnsatteOslo))
        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))

        val utledetEnhet = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "12345678911", emptyList())
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

        val service = EnhetService(graphClient, pdlKlient, nomKlient, NorgKlientMock(), VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", emptyList())
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


        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList())
        assertThat(res.enhet).isEqualTo(egneAnsatteOslo)
    }

    @Test
    fun `Skal ikke sette oppfølgingsenhet for egen ansatt`() {
        val egneAnsatteOslo = "0383"
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT)))
            )
        )
        val norgKlient = NorgKlientMock.medRespons(responsEnhet = (egneAnsatteOslo))
        val nomKlient = NomKlientMock.medRespons(erEgenansatt = true)
        val veilarbarenaClient = VeilarbarenaKlientMock(oppfølgingsenhet = "1111")

        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, veilarbarenaClient, UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList())
        assertThat(res.enhet).isEqualTo(egneAnsatteOslo)
        assertThat(res.oppfølgingsenhet).isNull()

        val res2 = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "any", emptyList())
        assertThat(res2.enhet).isEqualTo(egneAnsatteOslo)
        assertThat(res2.oppfølgingsenhet).isNull()
    }

    @Test
    fun `Vikafossen skal overstyre egne ansatte`() {
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.STRENGT_FORTROLIG)))
            )
        )
        val nomKlient = NomKlientMock.medRespons(erEgenansatt = true)

        val service = EnhetService(graphClient, pdlKlient, nomKlient, NorgKlientMock(), VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", emptyList())
        assertThat(res.enhet).isEqualTo(Enhet.NAV_VIKAFOSSEN.kode)
    }

    @Test
    fun `Skal sette veileders enhet til NAV_UTLAND for saker knyttet til brukere fra Danmark`() {
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(
                    gtType = GeografiskTilknytningType.UTLAND,
                    gtLand = "DNK"
                )
            )
        )

        val norgKlient = NorgKlientMock.medRespons()

        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(VEILEDER_AVKLARINGSBEHOVKODE, "any", emptyList())

        assertThat(res.enhet).isEqualTo(Enhet.NAV_UTLAND.kode)
    }

    @Test
    fun `Skal sette kvalitetssikrers enhet til NAV_UTLAND for saker knyttet til brukere fra Danmark`() {
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(
                    gtType = GeografiskTilknytningType.UTLAND,
                    gtLand = "DNK"
                )
            )
        )

        val norgKlient = NorgKlientMock.medRespons()

        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(KVALITETSSIKRER_AVKLARINGSBEHOVKODE, "any", emptyList())

        assertThat(res.enhet).isEqualTo(Enhet.NAV_UTLAND.kode)
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
        val pdlKlient = PdlKlientMock.medRespons(
            PdlData(
                hentPerson = HentPersonResult(adressebeskyttelse = listOf(Gradering(Adressebeskyttelseskode.UGRADERT))),
                hentGeografiskTilknytning = GeografiskTilknytning(
                    gtType = GeografiskTilknytningType.UTLAND,
                    gtLand = "DNK"
                )
            )
        )

        val norgKlient = NorgKlientMock.medRespons()

        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", emptyList())

        assertThat(res.enhet).isEqualTo(Enhet.NAY_UTLAND.kode)
    }


    @Test
    fun `Skal sette adressebeskyttelse når relaterte identer har adressebeskyttelse`() {
        val pdlKlient = PdlKlientMock.medRespons(
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

        val norgKlient = NorgKlientMock.medRespons()

        val service = EnhetService(graphClient, pdlKlient, nomKlient, norgKlient, VeilarbarenaKlientMock(), UnleashService(FakeUnleash().apply {
            enableAll()
        }))
        val res = service.utledEnhetForOppgave(NAY_AVKLARINGSBEHOVKODE, "any", listOf("barn"))

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

            override fun hentFortroligAdresseGruppe(currentToken: String): MemberOf {
                return MemberOf()
            }
        }

        val nomKlient = object : SkjermingKlient {
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

            override fun hentPersoninfoForIdenter(identer: List<String>): PdlData {
                TODO("Not yet implemented")
            }

            override fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData {
                return PdlData(
                    hentPersonBolk = emptyList(),
                )
            }
        }

        class NomKlientMock(val erEgenansatt: Boolean?) : SkjermingKlient {
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

            override fun hentPersoninfoForIdenter(identer: List<String>): PdlData {
                return pdlDataRespons ?: TODO("Not yet implemented")
            }

            override fun hentAdressebeskyttelseForIdenter(identer: List<String>): PdlData {
                return pdlDataRespons ?: TODO("Not yet implemented")
            }
        }

        class NorgKlientMock(
            val responsEnhet: String? = null,
            val enhetsNavnRespons: Map<String, String>? = null,
            val overordnetFylkesEnheter: List<String>? = null,
            val enhetTilOverordnetEnhetMap: Map<String, List<String>>? = null,
        ) : INorgKlient {
            companion object {
                fun medRespons(
                    responsEnhet: String? = null,
                    enhetsNavnRespons: Map<String, String>? = null,
                    overordnetFylkesEnheter: List<String>? = null,
                    enhetTilOverordnetEnhetMap: Map<String, List<String>>? = null
                ): NorgKlientMock {
                    return NorgKlientMock(responsEnhet, enhetsNavnRespons, overordnetFylkesEnheter, enhetTilOverordnetEnhetMap)
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

        class VeilarbarenaKlientMock(
            val oppfølgingsenhet: String? = null
        ) : IVeilarbarenaClient {
            companion object {
                fun medRespons(oppfølgingsenhet: String? = null): VeilarbarenaKlientMock {
                    return VeilarbarenaKlientMock(oppfølgingsenhet)
                }
            }

            override fun hentOppfølgingsenhet(personIdent: String): String? {
                return oppfølgingsenhet
            }
        }
    }
}