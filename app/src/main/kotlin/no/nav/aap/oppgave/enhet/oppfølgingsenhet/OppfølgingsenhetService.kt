package no.nav.aap.oppgave.enhet.oppfølgingsenhet

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.arena.ArenaNedeFake
import no.nav.aap.oppgave.klienter.arena.IVeilarbarenaGateway
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OppfølgingsenhetService(
    private val dataSource: DataSource,
    veilarbarenaGateway: IVeilarbarenaGateway,
    unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService()
) {
    private val log = LoggerFactory.getLogger(OppfølgingsenhetService::class.java)

    private val gateway = if (unleashService.isEnabled(FeatureToggles.BrukArenaFake) && !Miljø.erProd()) {
        log.info("Feature toggle for å bruke ArenaFake er skrudd på, bruker fake for å hente oppfølgingsenhet.")
        ArenaNedeFake()
    } else {
        veilarbarenaGateway
    }

    fun hentOppfølgingsenhet(personIdent: String): String? {
        return try {
            gateway.hentOppfølgingsenhet(personIdent)
        } catch (_: Exception) {
            dataSource.transaction(readOnly = true) { connection ->
                // OBS: vil gi feil svar i tilfeller der oppfølgingsenhet tidligere var satt, men så har blitt fjernet etter at saken kom inn i Kelvin
                OppgaveRepository(connection).hentSisteOppfølgingsenhetForPersonIdent(personIdent)
            }
                .also { log.info("Kunne ikke hente oppfølgingsenhet fra Arena, bruker forrige lagrede oppfølgingsenhet istedet.") }
        }
    }
}