package no.nav.aap.oppgave.klienter.pdl

import no.nav.aap.oppgave.klienter.graphql.asQuery

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

        fun hentPersoninfoForIdenter(identer: List<String>) = PdlRequest(
            query = PERSONINFO_BOLK_QUERY.asQuery(),
            variables = Variables(identer = identer)
        )

        fun hentAdressebeskyttelseForIdenter(identer: List<String>) = PdlRequest(
            query = ADRESSEBESKYTTELSE_BOLK_QUERY.asQuery(),
            variables = Variables(identer = identer)
        )
    }
}

private const val ident = "\$ident"
private const val identer = "\$identer"

val ADRESSEBESKYTTELSE_BOLK_QUERY = """
        query($identer: [ID!]!) {
            hentPersonBolk(identer: $identer) {
                ident,
                person {
                    adressebeskyttelse(historikk: false) {
                        gradering
                    }
                },
                code
            }
        }
   """.trimIndent()

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


val PERSONINFO_BOLK_QUERY = """
    query($identer: [ID!]!) {
        hentPersonBolk(identer: $identer) {
            ident,
            person {
                navn(historikk: false) {
                    fornavn
                    mellomnavn
                    etternavn
                }
            },
            code
        }
    }
""".trimIndent()