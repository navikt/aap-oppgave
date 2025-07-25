package no.nav.aap.oppgave.oppgaveliste

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient
import no.nav.aap.oppgave.plukk.TilgangGateway
import no.nav.aap.tilgang.Operasjon
import kotlin.collections.map
import kotlin.collections.mapNotNull

fun List<OppgaveDto>.hentPersonNavnMedTilgangssjekk(
    token: OidcToken,
    operasjon: Operasjon = Operasjon.SAKSBEHANDLE
): List<OppgaveDto> {
    val oppgaverMedNavn = this.hentPersonNavn()
    return oppgaverMedNavn.map {
        if (skalFjerneSensitivInformasjon(it, token, operasjon)) {
            // fjern sensitive felter
            it.copy(personNavn = null, personIdent = null, enhet = "", oppfølgingsenhet = null)
        } else {
            it
        }
    }
}

fun List<OppgaveDto>.hentPersonNavn(): List<OppgaveDto> {
    val identer = mapNotNull { it.personIdent }
    if (identer.isEmpty()) {
        return this
    }
    val navnMap =
        PdlGraphqlKlient
            .withClientCredentialsRestClient()
            .hentPersoninfoForIdenter(identer)
            ?.hentPersonBolk
            ?.associate {
                it.ident to it.person?.navn?.firstOrNull()
            } ?: emptyMap()

    return map {
        val personIdent = it.personIdent
        val personNavn =
            if (personIdent != null) {
                navnMap[personIdent]?.fulltNavn() ?: ""
            } else {
                ""
            }
        it.copy(personNavn = personNavn)
    }
}

private fun skalFjerneSensitivInformasjon(
    oppgaveDto: OppgaveDto,
    token: OidcToken,
    operasjon: Operasjon
) = TilgangGateway.sjekkTilgang(oppgaveDto.tilAvklaringsbehovReferanseDto(), token, operasjon) == false

fun harAdressebeskyttelse(oppgave: OppgaveDto): Boolean =
    (
        oppgave.enhet == Enhet.NAV_VIKAFOSSEN.kode ||
            oppgave.enhet.endsWith("83") || // alle kontorer for egen ansatt slutter på 83
            oppgave.harFortroligAdresse == true
    )