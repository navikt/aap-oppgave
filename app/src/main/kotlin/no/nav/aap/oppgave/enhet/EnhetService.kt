package no.nav.aap.oppgave.enhet

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.klienter.arena.IVeilarbarenaClient
import no.nav.aap.oppgave.klienter.arena.VeilarbarenaClient
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.nom.INomKlient
import no.nav.aap.oppgave.klienter.nom.NomKlient
import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.oppgave.klienter.norg.INorgKlient
import no.nav.aap.oppgave.klienter.norg.NorgKlient
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytning
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytningType
import no.nav.aap.oppgave.klienter.pdl.IPdlKlient
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient

data class EnhetForOppgave(
    val enhet: String,
    val oppfølgingsenhet: String?,
)

data class TilknytningOgSkjerming(
    val geografiskTilknytning: GeografiskTilknytning?,
    val diskresjonskode: Diskresjonskode,
    val erNavAnsatt: Boolean
)

interface IEnhetService {
    fun hentEnheter(currentToken: String, ident: String): List<String>
    fun utledEnhetForOppgave(avklaringsbehovKode: AvklaringsbehovKode, ident: String?, relevanteIdenter: List<String>): EnhetForOppgave
    fun skalHaFortroligAdresse(ident: String?, relevanteIdenter: List<String>): Boolean
}

class EnhetService(
    private val msGraphClient: IMsGraphClient,
    private val pdlGraphqlKlient: IPdlKlient = PdlGraphqlKlient.withClientCredentialsRestClient(),
    private val nomKlient: INomKlient = NomKlient(),
    private val norgKlient: INorgKlient = NorgKlient(),
    private val veilarbarenaKlient: IVeilarbarenaClient = VeilarbarenaClient(),
) : IEnhetService {

    override fun hentEnheter(currentToken: String, ident: String): List<String> {
        return msGraphClient.hentEnhetsgrupper(currentToken, ident).groups
            .map { it.name.removePrefix(ENHET_GROUP_PREFIX) }

    }

    override fun utledEnhetForOppgave(avklaringsbehovKode: AvklaringsbehovKode, ident: String?, relevanteIdenter: List<String>): EnhetForOppgave {
        return if (avklaringsbehovKode in
            AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
            + AVKLARINGSBEHOV_FOR_BESLUTTER
            + AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
        ) {
            requireNotNull(ident) { "fødselsnummer trenges for utlede enhet for ikke-kvalitetssikringsoppgaver" }
            finnNayEnhet(ident, relevanteIdenter)
        } else {
            if (avklaringsbehovKode.kode == Definisjon.KVALITETSSIKRING.kode.name) {
                finnFylkesEnhet(ident, relevanteIdenter)
            } else {
                finnEnhetstilknytningForPerson(ident, relevanteIdenter)
            }
        }
    }

    override fun skalHaFortroligAdresse(ident: String?, relevanteIdenter: List<String>): Boolean {
        val søkersGradering = finnTilknytningOgSkjerming(ident).diskresjonskode
        return finnStrengesteGradering(søkersGradering, relevanteIdenter) == Diskresjonskode.SPFO
    }

    fun kanSaksbehandleFortroligAdresse(
        currentToken: String
    ): Boolean {
        return msGraphClient.hentFortroligAdresseGruppe(currentToken).groups
            .map { it.name }.contains(FORTROLIG_ADRESSE_GROUP)
    }

    private fun finnFylkesEnhet(ident: String?, relevanteIdenter: List<String>): EnhetForOppgave {
        val enhet = finnEnhetstilknytningForPerson(ident, relevanteIdenter)
        if (enhet.enhet == Enhet.NAV_VIKAFOSSEN.kode || erEgneAnsatteKontor(enhet.enhet)) {
            return enhet
        }

        // Hvis enheten er NAV-Utland, så skal også kvalitetssikrer være NAV-Utland
        // Dette er et unntak fra hovedregel om at vi skal bruke overordnet enhet fra NORG
        // og må derfor spesialhåndteres
        if (enhet.enhet == Enhet.NAV_UTLAND.kode) {
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
        val enheterMedSammeFørste2Siffer = enheter.filter { it.substring(0, 2) == enhetsnummer.substring(0, 2) }

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
        } else if (geografiskTilknytning?.gtType == GeografiskTilknytningType.UTLAND) {
            Enhet.NAY_UTLAND.kode
        } else {
            Enhet.NAY.kode
        }

        return EnhetForOppgave(
            enhet,
            oppfølgingsenhet = null
        )
    }

    private fun finnEnhetstilknytningForPerson(ident: String?, relevanteIdenter: List<String>): EnhetForOppgave {
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
            val egenAnsatt = nomKlient.erEgenansatt(ident)
            return TilknytningOgSkjerming(
                geografiskTilknytning,
                diskresjonskode,
                egenAnsatt
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
        val graderingerForRelevanteIdenter = pdlGraphqlKlient.hentAdressebeskyttelseForIdenter(relevanteIdenter).hentPersonBolk?.flatMap { it.person?.adressebeskyttelse ?: emptyList() }?.map { it.gradering.tilDiskresjonskode() }
        val alleGraderinger = graderingerForRelevanteIdenter?.plus(søkersGradering) ?: listOf(søkersGradering)
        return alleGraderinger.max()
    }

    private fun erEgneAnsatteKontor(enhet: String): Boolean {
        return enhet.endsWith("83")
    }


    companion object {
        const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
        const val FORTROLIG_ADRESSE_GROUP = "0000-GA-Fortrolig_Adresse"
    }

}
