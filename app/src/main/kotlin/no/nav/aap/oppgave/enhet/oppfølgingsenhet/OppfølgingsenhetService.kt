package no.nav.aap.oppgave.enhet.oppfølgingsenhet

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.arena.IVeilarbarenaGateway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OppfølgingsenhetService(
    private val dataSource: DataSource,
    private val veilarbarenaGateway: IVeilarbarenaGateway,
) {
    private val log = LoggerFactory.getLogger(OppfølgingsenhetService::class.java)

    fun hentOppfølgingsenhet(personIdent: String): String? {
        return try {
            veilarbarenaGateway.hentOppfølgingsenhet(personIdent)
        } catch (_: Exception) {
            dataSource.transaction(readOnly = true) { connection ->
                // OBS: vil gi feil svar i tilfeller der oppfølgingsenhet tidligere var satt, men så har blitt fjernet etter at saken kom inn i Kelvin
                OppgaveRepository(connection).hentSisteOppfølgingsenhetForPersonIdent(personIdent)
            }
                .also { log.info("Kunne ikke hente oppfølgingsenhet fra Arena, bruker forrige lagrede oppfølgingsenhet istedet.") }
        }
    }
}