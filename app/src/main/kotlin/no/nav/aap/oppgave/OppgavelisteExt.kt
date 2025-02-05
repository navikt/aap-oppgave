package no.nav.aap.oppgave

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient
import no.nav.aap.oppgave.plukk.TilgangGateway

fun List<OppgaveDto>.medPersonNavn(fjernNavnNårTilgangMangler: Boolean, token: OidcToken): List<OppgaveDto> {
    val identer = mapNotNull { it.personIdent }
    if (identer.isEmpty()) {
        return this
    }
    val navnMap = PdlGraphqlKlient.withClientCredentialsRestClient()
        .hentPersoninfoForIdenter(identer)?.hentPersonBolk?.associate {
            it.ident to it.person?.navn?.firstOrNull()
        } ?: emptyMap()

    val oppgaverMedNavn =  map {
        val personIdent = it.personIdent
        val personNavn = if (personIdent != null) {
            navnMap[personIdent]?.fulltNavn() ?: ""
        } else {
            ""
        }
        it.copy(personNavn = personNavn)
    }
    return if (fjernNavnNårTilgangMangler) {
        oppgaverMedNavn.map {
            if (skalFjerneNavnOgFnr(it, token)) {
                it.copy(personNavn = null, personIdent = null)
            } else {
                it
            }
        }
    } else {
        oppgaverMedNavn
    }
}

private fun skalFjerneNavnOgFnr(oppgaveDto: OppgaveDto, token: OidcToken) =
    TilgangGateway.sjekkTilgang(oppgaveDto.tilAvklaringsbehovReferanseDto(), token)
