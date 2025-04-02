package no.nav.aap.oppgave.enhet

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
import no.nav.aap.oppgave.prosessering.NAV_VIKAFOSSEN

data class EnhetForOppgave(
    val enhet: String,
    val oppfølgingsenhet: String?,
)

interface IEnhetService {
    fun hentEnheter(currentToken: String, ident: String): List<String>
    fun finnEnhetForOppgave(fnr: String?): EnhetForOppgave
    fun finnFortroligAdresse(fnr: String): Diskresjonskode
    fun finnFylkesEnhet(fnr: String?): EnhetForOppgave
}

class EnhetService(
    private val msGraphClient: IMsGraphClient,
    private val pdlGraphqlKlient: IPdlKlient = PdlGraphqlKlient.withClientCredentialsRestClient(),
    private val nomKlient: INomKlient = NomKlient(),
    private val norgKlient: INorgKlient = NorgKlient(),
    private val veilarbarenaKlient: IVeilarbarenaClient = VeilarbarenaClient()
) : IEnhetService {

    override fun hentEnheter(currentToken: String, ident: String): List<String> {
        return msGraphClient.hentEnhetsgrupper(currentToken, ident).groups
            .map { it.name.removePrefix(ENHET_GROUP_PREFIX) }

    }

    override fun finnFylkesEnhet(fnr: String?): EnhetForOppgave {
        val enhet = finnEnhetForOppgave(fnr)
        if (enhet.enhet == NAV_VIKAFOSSEN || erEgneAnsatteKontor(enhet.enhet)) {
            return enhet
        }
        return EnhetForOppgave(parseFylkeskontor(enhet.enhet), enhet.oppfølgingsenhet?.let { parseFylkeskontor(it) })
    }

    override fun finnEnhetForOppgave(fnr: String?): EnhetForOppgave {
        val tilknytningOgSkjerming = finnTilknytningOgSkjerming(fnr)
        val enhetFraNorg = norgKlient.finnEnhet(
            tilknytningOgSkjerming.geografiskTilknytningKode,
            tilknytningOgSkjerming.erNavAnsatt,
            tilknytningOgSkjerming.diskresjonskode
        )
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
        val geografiskTilknytningKode: String,
        val diskresjonskode: Diskresjonskode,
        val erNavAnsatt: Boolean
    )

    override fun finnFortroligAdresse(fnr: String): Diskresjonskode {
        val pdlData = pdlGraphqlKlient.hentAdressebeskyttelseOgGeolokasjon(fnr)
        return mapDiskresjonskode(pdlData.hentPerson?.adressebeskyttelse?.map { it.gradering })

    }

    private fun finnTilknytningOgSkjerming(fnr: String?): TilknytningOgSkjerming {
        if (fnr != null) {
            val pdlData = pdlGraphqlKlient.hentAdressebeskyttelseOgGeolokasjon(fnr)
            val geografiskTilknytning = pdlData.hentGeografiskTilknytning
            val geografiskTilknytningKode = if (geografiskTilknytning != null) {
                mapGeografiskTilknytningTilKode(geografiskTilknytning)
            } else {
                null
            }

            val diskresjonskode = mapDiskresjonskode(pdlData.hentPerson?.adressebeskyttelse?.map { it.gradering })
            val egenAnsatt = nomKlient.erEgenansatt(fnr)
            return TilknytningOgSkjerming(
                geografiskTilknytningKode ?: GeografiskTilknytningType.UDEFINERT.name,
                diskresjonskode,
                egenAnsatt
            )
        } else {
            return TilknytningOgSkjerming(
                GeografiskTilknytningType.UDEFINERT.name,
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

    private fun parseFylkeskontor(enhet: String): String {
        return enhet.substring(0, 2) + "00"
    }

    private fun erEgneAnsatteKontor(enhet: String): Boolean {
        return enhet.endsWith("83")
    }


    companion object {
        const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
    }

}
