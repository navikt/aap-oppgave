package no.nav.aap.oppgave.enhet

import no.nav.aap.oppgave.klienter.arena.VeilarbarenaClient
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.nom.NomKlient
import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.oppgave.klienter.norg.NorgKlient
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytning
import no.nav.aap.oppgave.klienter.pdl.GeografiskTilknytningType
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient
import org.slf4j.LoggerFactory

data class EnhetForOppgave(
    val enhet: String,
    val oppfølgingsenhet: String?,
)

class EnhetService(private val msGraphClient: IMsGraphClient) {

    private val log = LoggerFactory.getLogger(EnhetService::class.java)

    fun hentEnheter(currentToken: String, ident: String): List<String> {
        return try {
            log.info("Henter enheter med filter")
            val enheter = msGraphClient.hentEnhetsgrupper(currentToken, ident).groups
                .map { it.name.removePrefix(ENHET_GROUP_PREFIX) }
            if (enheter.isEmpty()) {
                log.warn("Fant ingen enheter for bruker, henter alle grupper")
                msGraphClient.hentAdGrupper(currentToken, ident).groups
                    .filter { it.name.startsWith(ENHET_GROUP_PREFIX) }
                    .map { it.name.removePrefix(ENHET_GROUP_PREFIX) }
            } else {
                enheter
            }
        } catch (e: Exception) {
            log.info("Klarte ikke utføre filtrert gruppespørring, henter alle grupper", e)
            msGraphClient.hentAdGrupper(currentToken, ident).groups
                .filter { it.name.startsWith(ENHET_GROUP_PREFIX) }
                .map { it.name.removePrefix(ENHET_GROUP_PREFIX) }
        }
    }

    fun finnEnhetForOppgave(fnr: String?): EnhetForOppgave {
        val tilknytningOgSkjerming = finnTilknytningOgSkjerming(fnr)
        val enhetFraNorg = NorgKlient().finnEnhet(
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
            VeilarbarenaClient().hentOppfølgingsenhet(fnr)
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

    private fun finnTilknytningOgSkjerming(fnr: String?): TilknytningOgSkjerming {
        if (fnr != null) {
            val pdlData = PdlGraphqlKlient.withClientCredentialsRestClient().hentAdressebeskyttelseOgGeolokasjon(fnr)
            val geografiskTilknytning = pdlData.hentGeografiskTilknytning
            val geografiskTilknytningKode = if (geografiskTilknytning != null) {
                mapGeografiskTilknytningTilKode(geografiskTilknytning)
            } else {
                null
            }

            val diskresjonskode = mapDiskresjonskode(pdlData.hentPerson?.adressebeskyttelse?.map { it.gradering })
            val egenAnsatt = NomKlient().erEgenansatt(fnr)
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


    companion object {
        const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
    }

}
