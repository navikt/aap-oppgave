package no.nav.aap.oppgave.klienter.arena

import no.nav.aap.komponenter.httpklient.httpclient.error.InternalServerErrorHttpResponsException

class ArenaNedeFake: IVeilarbarenaGateway {
    override fun hentOppfølgingsenhet(personIdent: String): String? {
        throw InternalServerErrorHttpResponsException("Arena er nede")
    }
}