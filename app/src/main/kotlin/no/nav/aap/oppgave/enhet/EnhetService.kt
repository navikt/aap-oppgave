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
import no.nav.aap.oppgave.klienter.pdl.PdlData
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient

data class EnhetForOppgave(
    val enhet: String,
    val oppfølgingsenhet: String?,
)

interface IEnhetService {
    fun hentEnheter(currentToken: String, ident: String): List<String>
    fun utledEnhetForOppgave(avklaringsbehovKode: AvklaringsbehovKode, fnr: String?): EnhetForOppgave
    fun harFortroligAdresse(personIdent: String?): Boolean
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

    override fun utledEnhetForOppgave(avklaringsbehovKode: AvklaringsbehovKode, fnr: String?): EnhetForOppgave {
        return if (avklaringsbehovKode in
            AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
            + AVKLARINGSBEHOV_FOR_BESLUTTER
            + AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
        ) {
            finnNayEnhet(fnr!!)
        } else {
            if (avklaringsbehovKode.kode == Definisjon.KVALITETSSIKRING.kode.name) {
                finnFylkesEnhet(fnr)
            } else {
                finnEnhetstilknytningForPerson(fnr)
            }
        }
    }

    override fun harFortroligAdresse(personIdent: String?): Boolean {
        return finnTilknytningOgSkjerming(personIdent).diskresjonskode == Diskresjonskode.SPFO
    }

    fun kanSaksbehandleFortroligAdresse(
        currentToken: String
    ): Boolean {
        return msGraphClient.hentFortroligAdresseGruppe(currentToken).groups
            .map { it.name }.contains(FORTROLIG_ADRESSE_GROUP)
    }

    private fun finnFylkesEnhet(fnr: String?): EnhetForOppgave {
        val enhet = finnEnhetstilknytningForPerson(fnr)
        if (enhet.enhet == Enhet.NAV_VIKAFOSSEN.kode || erEgneAnsatteKontor(enhet.enhet)) {
            return enhet
        }

        // Hvis enheten er NAV-Utland, så skal også kvalitetssikrer være NAV-Utland
        // Dette er et unntak fra hovedregel om at vi skal bruke overordnet enhet fra NORG
        // og må derfor spesialhåndteres
        if (enhet.enhet == Enhet.NAV_UTLAND.kode) {
            return EnhetForOppgave(
                enhet = Enhet.NAV_UTLAND.kode,
                oppfølgingsenhet = enhet.oppfølgingsenhet?.let { norgKlient.hentOverordnetFylkesenhet(it) }
            )
        }

        return EnhetForOppgave(
            enhet = norgKlient.hentOverordnetFylkesenhet(enhet.enhet),
            oppfølgingsenhet = enhet.oppfølgingsenhet?.let { norgKlient.hentOverordnetFylkesenhet(it) }
        )
    }

    private fun finnEnhetstilknytningForPerson(fnr: String?): EnhetForOppgave {
        val tilknytningOgSkjerming = finnTilknytningOgSkjerming(fnr)

        // Hvis personen er utenlandsk, så vil NORG returnere feil kontor (Den returnerer NAY-kontoret).
        // Vi må derfor hardkode inn dette som et unntak.
        val enhetFraNorg = if (tilknytningOgSkjerming.geografiskTilknytning?.gtType == GeografiskTilknytningType.UTLAND) {
            Enhet.NAV_UTLAND.kode
        } else {
            norgKlient.finnEnhet(
                tilknytningOgSkjerming.geografiskTilknytning?.let { mapGeografiskTilknytningTilKode(it) },
                tilknytningOgSkjerming.erNavAnsatt,
                tilknytningOgSkjerming.diskresjonskode
            )
        }
        val enhetFraArena = if (tilknytningOgSkjerming.diskresjonskode != Diskresjonskode.SPSF) {
            finnOppfølgingsenhet(fnr)
        } else {
            null
        }
        return EnhetForOppgave(enhetFraNorg, enhetFraArena)
    }

    private fun finnOppfølgingsenhet(fnr: String?): String? {
        val enhetFraArena = if (fnr != null) {
            veilarbarenaKlient.hentOppfølgingsenhet(fnr)
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

    data class TilknytningOgSkjerming(
        val geografiskTilknytning: GeografiskTilknytning?,
        val diskresjonskode: Diskresjonskode,
        val erNavAnsatt: Boolean
    )

    private fun finnTilknytningOgSkjerming(fnr: String?): TilknytningOgSkjerming {
        if (fnr != null) {
            val pdlData = pdlGraphqlKlient.hentAdressebeskyttelseOgGeolokasjon(fnr)
            val geografiskTilknytning = pdlData.hentGeografiskTilknytning

            val diskresjonskode = mapDiskresjonskode(pdlData.hentPerson?.adressebeskyttelse?.map { it.gradering })
            val egenAnsatt = nomKlient.erEgenansatt(fnr)
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

    private fun finnNayEnhet(fnr: String): EnhetForOppgave {
        val pdlData = pdlGraphqlKlient.hentAdressebeskyttelseOgGeolokasjon(fnr)

        val erStrengtFortrolig = harStrengtFortroligAdresse(pdlData)
        val geografiskTilknytning = pdlData.hentGeografiskTilknytning
        val erEgenAnsatt = nomKlient.erEgenansatt(fnr)
        
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

    private fun harStrengtFortroligAdresse(pdlData: PdlData): Boolean {
        val diskresjonskode = mapDiskresjonskode(pdlData.hentPerson?.adressebeskyttelse?.map { it.gradering })
        return diskresjonskode == Diskresjonskode.SPSF
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

    private fun erEgneAnsatteKontor(enhet: String): Boolean {
        return enhet.endsWith("83")
    }


    companion object {
        const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
        const val FORTROLIG_ADRESSE_GROUP = "0000-GA-Fortrolig_Adresse"
    }

}
