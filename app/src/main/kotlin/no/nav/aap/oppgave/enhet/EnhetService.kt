package no.nav.aap.oppgave.enhet

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.klienter.arena.IVeilarbarenaClient
import no.nav.aap.oppgave.klienter.arena.VeilarbarenaClient
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.nom.skjerming.NomSkjermingKlient
import no.nav.aap.oppgave.klienter.nom.skjerming.SkjermingKlient
import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.oppgave.klienter.norg.INorgKlient
import no.nav.aap.oppgave.klienter.norg.NorgKlient
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytning
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytningType
import no.nav.aap.oppgave.klienter.pdl.IPdlKlient
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.postmottak.kontrakt.enhet.GodkjentEnhet
import org.slf4j.LoggerFactory

data class EnhetForOppgave(
    val enhet: String,
    val oppfølgingsenhet: String?,
)

data class TilknytningOgSkjerming(
    val geografiskTilknytning: GeografiskTilknytning?,
    val diskresjonskode: Diskresjonskode,
    val erNavAnsatt: Boolean
)

enum class FylkeskontorSomSkalBehandleKlager(val enhetsnummer: String) {
    NAV_VEST_VIKEN("0600")
}

interface IEnhetService {
    fun hentEnheter(ident: String, currentToken: OidcToken): List<String>
    fun utledEnhetForOppgave(avklaringsbehovKode: AvklaringsbehovKode, ident: String?, relevanteIdenter: List<String>, saksnummer: String? = null): EnhetForOppgave
    fun skalHaFortroligAdresse(ident: String?, relevanteIdenter: List<String>): Boolean
}


/**
 * Husk å vedlikeholde tekstlig beskrivelse i sysdoc
 * https://aap-sysdoc.ansatt.nav.no/funksjonalitet/Oppgave/
 */
class EnhetService(
    private val msGraphClient: IMsGraphClient,
    private val pdlGraphqlKlient: IPdlKlient = PdlGraphqlKlient.withClientCredentialsRestClient(),
    private val nomSkjermingKlient: SkjermingKlient = NomSkjermingKlient(),
    private val norgKlient: INorgKlient = NorgKlient(),
    private val veilarbarenaKlient: IVeilarbarenaClient = VeilarbarenaClient(),
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService()
) : IEnhetService {
    private val log = LoggerFactory.getLogger(EnhetService::class.java)

    override fun hentEnheter(ident: String, currentToken: OidcToken): List<String> {
        return msGraphClient.hentEnhetsgrupper(ident, currentToken).groups
            .map { it.name.removePrefix(ENHET_GROUP_PREFIX) }
    }

    override fun utledEnhetForOppgave(avklaringsbehovKode: AvklaringsbehovKode, ident: String?, relevanteIdenter: List<String>, saksnummer: String?): EnhetForOppgave {
        return if (avklaringsbehovKode in
            AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
            + AVKLARINGSBEHOV_FOR_BESLUTTER
            + AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
        ) {
            requireNotNull(ident) { "Kan ikke utlede oppgavens enhet uten ident. Saksnummer $saksnummer" }
            finnNayEnhet(ident, relevanteIdenter)
        } else {
            when (avklaringsbehovKode.kode) {
                Definisjon.KVALITETSSIKRING.kode.name -> {
                    finnFylkesEnhet(ident, relevanteIdenter, saksnummer)
                }
                Definisjon.VURDER_KLAGE_KONTOR.kode.name if unleashService.isEnabled(
                    FeatureToggles.NyRutingAvKlageoppgaver
                ) -> {
                    finnEnhetForKlageoppgave(ident, relevanteIdenter, saksnummer)
                }
                else -> {
                    finnEnhetstilknytningForPerson(ident, relevanteIdenter, saksnummer)
                }
            }
        }
    }

    private fun finnEnhetForKlageoppgave(ident: String?, relevanteIdenter: List<String>, saksnummer: String?): EnhetForOppgave {
        // Ruter klageoppgave til fylkeskontor for de fylkene som har bedt om det
        val fylkesenhetForOppgave = finnFylkesEnhet(ident, relevanteIdenter, saksnummer)
        val gjeldendeFylkesenhet = fylkesenhetForOppgave.oppfølgingsenhet ?: fylkesenhetForOppgave.enhet
        if (gjeldendeFylkesenhet in FylkeskontorSomSkalBehandleKlager.entries.map { it.enhetsnummer }) {
            log.info("Ruter klageoppgave til fylkesenhet. Saksnummer: $saksnummer")
            return fylkesenhetForOppgave
        }
        return finnEnhetstilknytningForPerson(ident, relevanteIdenter, saksnummer)
    }

    override fun skalHaFortroligAdresse(ident: String?, relevanteIdenter: List<String>): Boolean {
        val søkersGradering = finnTilknytningOgSkjerming(ident).diskresjonskode
        return finnStrengesteGradering(søkersGradering, relevanteIdenter) == Diskresjonskode.SPFO
    }

    fun kanSaksbehandleFortroligAdresse(
        ident: String,
        currentToken: OidcToken
    ): Boolean {
        return msGraphClient.hentFortroligAdresseGruppe(ident, currentToken).groups
            .any { it.name == FORTROLIG_ADRESSE_GROUP }
    }

    private fun finnFylkesEnhet(ident: String?, relevanteIdenter: List<String>, saksnummer: String?): EnhetForOppgave {
        val enhet = finnEnhetstilknytningForPerson(ident, relevanteIdenter, saksnummer)
        if (enhet.enhet == Enhet.NAV_VIKAFOSSEN.kode || erEgneAnsatteKontor(enhet.enhet)) {
            return enhet
        }

        // Hvis enheten eller oppfølgingsenheten er NAV-Utland, så skal også kvalitetssikrer være NAV-Utland
        // Dette er et unntak fra hovedregel om at vi skal bruke overordnet enhet fra NORG
        // og må derfor spesialhåndteres
        if (enhet.oppfølgingsenhet == Enhet.NAV_UTLAND.kode) {
            return EnhetForOppgave(
                enhet = getOverordnetEnhet(enhet.enhet),
                oppfølgingsenhet = Enhet.NAV_UTLAND.kode
            )
        } else if (enhet.enhet == Enhet.NAV_UTLAND.kode) {
            return EnhetForOppgave(
                enhet = Enhet.NAV_UTLAND.kode,
                oppfølgingsenhet = enhet.oppfølgingsenhet?.let { getOverordnetEnhet(it) }
            )
        }

        return EnhetForOppgave(
            enhet = getOverordnetEnhet(enhet.enhet),
            oppfølgingsenhet = enhet.oppfølgingsenhet?.let { getOverordnetEnhet(it) }
        )
    }

    private fun getOverordnetEnhet(enhetsnummer: String): String {
        val enheter = norgKlient.hentOverordnetFylkesenheter(enhetsnummer)
        val enheterMedSammeFørste2Siffer = enheter.filter { it.take(2) == enhetsnummer.take(2) }

        if (enheterMedSammeFørste2Siffer.isNotEmpty()) {
            return enheterMedSammeFørste2Siffer.first()
        }
        return enheter.first()
    }

    private fun finnNayEnhet(ident: String, relevanteIdenter: List<String>): EnhetForOppgave {
        val tilknytningOgSkjerming = finnTilknytningOgSkjerming(ident)
        val strengesteGradering = finnStrengesteGradering(tilknytningOgSkjerming.diskresjonskode, relevanteIdenter)

        val erStrengtFortrolig = strengesteGradering == Diskresjonskode.SPSF
        val geografiskTilknytning = tilknytningOgSkjerming.geografiskTilknytning
        val erEgenAnsatt = tilknytningOgSkjerming.erNavAnsatt

        val enhet = if (erStrengtFortrolig) {
            Enhet.NAV_VIKAFOSSEN.kode
        } else if (erEgenAnsatt) {
            Enhet.NAY_EGNE_ANSATTE.kode
        } else if (skalTilNayUtland(ident, geografiskTilknytning?.gtType)) {
            Enhet.NAY_UTLAND.kode
        } else {
            Enhet.NAY.kode
        }

        return EnhetForOppgave(
            enhet,
            oppfølgingsenhet = null
        )
    }

    private fun finnEnhetstilknytningForPerson(ident: String?, relevanteIdenter: List<String>, saksnummer: String?): EnhetForOppgave {
        val tilknytningOgSkjerming = finnTilknytningOgSkjerming(ident)
        val strengesteGradering = finnStrengesteGradering(tilknytningOgSkjerming.diskresjonskode, relevanteIdenter)

        // Hvis personen er utenlandsk, så vil NORG returnere feil kontor (Den returnerer NAY-kontoret).
        // Vi må derfor hardkode inn dette som et unntak.
        val enhetFraNorg =
            if (tilknytningOgSkjerming.geografiskTilknytning?.gtType == GeografiskTilknytningType.UTLAND) {
                Enhet.NAV_UTLAND.kode
            } else {
                norgKlient.finnEnhet(
                    tilknytningOgSkjerming.geografiskTilknytning?.let { mapGeografiskTilknytningTilKode(it) },
                    tilknytningOgSkjerming.erNavAnsatt,
                    strengesteGradering,
                )
            }
        val enhetFraArena =
            if (strengesteGradering != Diskresjonskode.SPSF && !tilknytningOgSkjerming.erNavAnsatt) {
                finnOppfølgingsenhet(ident).takeIf { it != Enhet.NASJONAL_OPPFØLGINGSENHET.kode }
            } else {
                null
            }

        val enhetForKø = enhetFraArena ?: enhetFraNorg
        val skalVarsle = !GodkjentEnhet.entries.any { it.enhetNr == enhetForKø } && enhetForKø != Enhet.NAV_UTLAND.kode
        if (skalVarsle && unleashService.isEnabled(FeatureToggles.VarsleHvisEnhetIkkeGodkjent)) {
            log.error("Oppgave har lagt seg på køen til enhet $enhetForKø, som ikke har tatt Kelvin i bruk enda. Saksnummer: $saksnummer")
        }

        return EnhetForOppgave(enhetFraNorg, enhetFraArena)
    }

    private fun finnOppfølgingsenhet(ident: String?): String? {
        val enhetFraArena = if (ident != null) {
            veilarbarenaKlient.hentOppfølgingsenhet(ident)
        } else {
            null
        }
        return enhetFraArena
    }

    private fun mapGeografiskTilknytningTilKode(geoTilknytning: GeografiskTilknytning) =
        when (geoTilknytning.gtType) {
            GeografiskTilknytningType.KOMMUNE ->
                geoTilknytning.gtKommune

            GeografiskTilknytningType.BYDEL ->
                geoTilknytning.gtBydel

            GeografiskTilknytningType.UTLAND ->
                geoTilknytning.gtLand

            GeografiskTilknytningType.UDEFINERT ->
                geoTilknytning.gtType.name
        }

    private fun finnTilknytningOgSkjerming(ident: String?): TilknytningOgSkjerming {
        if (ident != null) {
            val pdlData = pdlGraphqlKlient.hentAdressebeskyttelseOgGeolokasjon(ident)
            val geografiskTilknytning = pdlData.hentGeografiskTilknytning

            val diskresjonskode = mapDiskresjonskode(pdlData.hentPerson?.adressebeskyttelse?.map { it.gradering })
            val erSkjermet = nomSkjermingKlient.erSkjermet(ident)
            return TilknytningOgSkjerming(
                geografiskTilknytning,
                diskresjonskode,
                erSkjermet
            )
        } else {
            return TilknytningOgSkjerming(
                GeografiskTilknytning(GeografiskTilknytningType.UDEFINERT),
                Diskresjonskode.ANY,
                false
            )
        }
    }

    private fun mapDiskresjonskode(adressebeskyttelsekoder: List<Adressebeskyttelseskode>?) =
        adressebeskyttelsekoder?.firstOrNull().let {
            when (it) {
                Adressebeskyttelseskode.FORTROLIG ->
                    Diskresjonskode.SPFO

                Adressebeskyttelseskode.STRENGT_FORTROLIG, Adressebeskyttelseskode.STRENGT_FORTROLIG_UTLAND ->
                    Diskresjonskode.SPSF

                else -> Diskresjonskode.ANY
            }
        }

    private fun finnStrengesteGradering(søkersGradering: Diskresjonskode, relevanteIdenter: List<String> = emptyList()): Diskresjonskode {
        val graderingerForRelevanteIdenter = if (relevanteIdenter.isEmpty()) {
            emptyList()
        } else {
            pdlGraphqlKlient.hentAdressebeskyttelseForIdenter(relevanteIdenter).hentPersonBolk?.flatMap { it.person?.adressebeskyttelse ?: emptyList() }?.map { it.gradering.tilDiskresjonskode() }
        }
        val alleGraderinger = graderingerForRelevanteIdenter?.plus(søkersGradering) ?: listOf(søkersGradering)
        return alleGraderinger.max()
    }

    private fun erEgneAnsatteKontor(enhet: String): Boolean {
        return enhet.endsWith("83")
    }

    private fun skalTilNayUtland(ident: String, geografiskTilknytningType: GeografiskTilknytningType?): Boolean {
        val utlandTilknytning = geografiskTilknytningType == GeografiskTilknytningType.UTLAND
        // Hvis bruker har norsk adresse, men saken allikevel bør håndteres av utlands-enhetene,
        // settes oppfølgingsenhet til NUFO. Da skal saken videre til NAY Utland
        val utlandOppfølgingsenhet = finnOppfølgingsenhet(ident) == Enhet.NAV_UTLAND.kode
        return utlandTilknytning || utlandOppfølgingsenhet
    }

    companion object {
        private const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
        private const val FORTROLIG_ADRESSE_GROUP = "0000-GA-Fortrolig_Adresse"
    }

}
