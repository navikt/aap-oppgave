package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer

data class SaksnummerPathParam(
    @PathParam("saksnummer") val saksnummer: String
) {
    fun tilSaksnummer(): Saksnummer = Saksnummer(saksnummer)
}

