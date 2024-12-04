package no.nav.aap.oppgave.klienter.pdl

import no.nav.aap.postmottak.klient.graphql.asQuery

internal data class PdlRequest(val query: String, val variables: Variables) {
    data class Variables(val ident: String? = null, val identer: List<String>? = null)

    companion object {

        fun hentAdressebeskyttelseOgGeografiskTilknytning(ident: String) = PdlRequest(
            query = ADRESSEBESKYTTELSE_QUERY.asQuery(),
            variables = Variables(ident = ident)
        )
        
        fun hentGeografiskTilknytning(ident: String) = PdlRequest(
            query = GEOGRAFISK_TILKNYTNING_QUERY.asQuery(),
            variables = Variables(ident = ident)
        )
    }
}

private const val ident = "\$ident"

val ADRESSEBESKYTTELSE_QUERY = """
     query($ident: ID!) {
      hentPerson(ident: $ident) {
        adressebeskyttelse(historikk: false) {
          gradering
        }
      },
    
      hentGeografiskTilknytning(ident: $ident) {
        gtType
        gtKommune
        gtBydel
        gtLand
      }
    }   
""".trimIndent()

val GEOGRAFISK_TILKNYTNING_QUERY = """
    query($ident: ID!) {
        hentGeografiskTilknytning(ident: $ident) {
            gtType
            gtKommune
            gtBydel
            gtLand
        }
}
""".trimIndent()