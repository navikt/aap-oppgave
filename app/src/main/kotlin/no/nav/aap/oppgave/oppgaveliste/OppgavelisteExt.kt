package no.nav.aap.oppgave.oppgaveliste

import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlGateway
import kotlin.collections.map
import kotlin.collections.mapNotNull

fun List<OppgaveDto>.hentPersonNavn(): List<OppgaveDto> {
    val identer = mapNotNull { it.personIdent }.distinct()
    if (identer.isEmpty()) {
        return this
    }
    val navnMap =
        PdlGraphqlGateway
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

fun harAdressebeskyttelse(oppgave: OppgaveDto): Boolean =
    (
        oppgave.enhet == Enhet.NAV_VIKAFOSSEN.kode ||
            oppgave.enhet.endsWith("83") || // alle kontorer for egen ansatt slutter på 83
            oppgave.harFortroligAdresse == true
    )