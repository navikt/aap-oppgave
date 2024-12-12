package no.nav.aap.oppgave.klienter.pdl

import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.postmottak.klient.graphql.GraphQLError
import no.nav.aap.postmottak.klient.graphql.GraphQLExtensions
import java.time.LocalDate

internal data class PdlResponse(
    val data: PdlData?,
    val errors: List<GraphQLError>?,
    val extensions: GraphQLExtensions?
)

data class PdlData(
    val hentPerson: HentPersonResult? = null,
    val hentPersonBolk: List<HentPersonBolkResult>? = null,
    val hentIdenter: HentIdenterResult? = null,
    val hentGeografiskTilknytning: GeografiskTilknytning? = null
)

data class HentPersonBolkResult(
    val ident: String,
    val person: PdlPerson?,
    val code: String,
)

data class GeografiskTilknytning(
    val gtType: GeografiskTilknytningType,
    val gtKommune: String? = null,
    val gtBydel: String? = null,
    val gtLand: String? = null,
)

enum class GeografiskTilknytningType{
    KOMMUNE,
    BYDEL,
    UTLAND,
    UDEFINERT
}

data class PdlPerson(
    val navn: List<Navn>, val code: Code?     //Denne er påkrevd ved hentPersonBolk
)

data class HentPersonResult(
    val foedselsdato: List<Fødselsdato>? = null,
    val adressebeskyttelse: List<Adressebeskyttelseskode>? = null
)

data class Fødselsdato(val foedselsdato: LocalDate)

enum class Code {
    ok, not_found, bad_request
}

data class Navn(
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?
) {
    fun fulltNavn(): String {
        return "${fornavn ?: ""} ${mellomnavn ?: ""} ${etternavn ?: ""}".trim()
    }
}

data class HentIdenterResult(val identer: List<PdlIdent>)

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: PdlGruppe
)

enum class PdlGruppe {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}

enum class Adressebeskyttelseskode {
    FORTROLIG,
    STRENGT_FORTROLIG,
    STRENGT_FORTROLIG_UTLAND,
    UGRADERT;

    fun tilDiskresjonskode() =
        when (this) {
            FORTROLIG -> Diskresjonskode.SPFO
            STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND -> Diskresjonskode.SPSF
            UGRADERT -> Diskresjonskode.ANY
        }
}