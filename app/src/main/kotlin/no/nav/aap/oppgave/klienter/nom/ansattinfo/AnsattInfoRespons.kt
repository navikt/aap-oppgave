package no.nav.aap.oppgave.klienter.nom.ansattinfo

import no.nav.aap.oppgave.klienter.graphql.GraphQLError

data class AnsattInfoRespons(
    val data: AnsattInfoData?,
    val errors: List<GraphQLError>?,
)

data class AnsattInfoData(
    val ressurs: NomRessurs?,
)

data class NomRessurs(
    val visningsnavn: String,
)

data class AnsattSøkResponse(
    val data: AnsattSøkData?,
    val errors: List<GraphQLError>?,
)

data class AnsattSøkData(
    val search: List<AnsattFraSøk>
)

data class AnsattFraSøk(
    val visningsnavn: String,
    val navident: String,
    val orgTilknytning: List<OrgEnhetInfo>
)

data class OrgEnhetInfo (
    val orgEnhet: OrgEnhet
)

data class OrgEnhet(
    val orgEnhetsType: OrgEnhetsType,
)

enum class OrgEnhetsType {
    ARBEIDSLIVSSENTER,
    NAV_ARBEID_OG_YTELSER,
    ARBEIDSRAADGIVNING,
    DIREKTORAT,
    DIR,
    FYLKE,
    NAV_FAMILIE_OG_PENSJONSYTELSER,
    HJELPEMIDLER_OG_TILRETTELEGGING,
    KLAGEINSTANS,
    NAV_KONTAKTSENTER,
    KONTROLL_KONTROLLENHET,
    NAV_KONTOR,
    TILTAK,
    NAV_OKONOMITJENESTE
}