package no.nav.aap.oppgave.enhet

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.norg.NorgKlient
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident


fun NormalOpenAPIRoute.hentEnhetApi(msGraphClient: IMsGraphClient, prometheus: PrometheusMeterRegistry) =

    route("/enheter").get<Unit, List<EnhetDto>> {
        prometheus.httpCallCounter("/enheter").increment()
        val enheter = EnhetService(msGraphClient).hentEnheter(token().token(), ident())
        val enhetNrTilNavn = NorgKlient().hentEnheter()
        val enheterMedNavn = enheter.map { EnhetDto(it, enhetNrTilNavn[it] ?: "") }
        respond(enheterMedNavn)
    }

/*
 * Kode brukt for å populere enhetsfeltet på oppgave på gamle oppgaver. Lar det ligge en stund i tilfelle
 * det skal bli aktuelt å gjøre det igjen.
data class EnhetsoppdateringRapport(val antallOppgaverUtenEnhet: Int, val oppgaveOgPersonListe: List<OppgaveOgPerson>)

fun NormalOpenAPIRoute.oppdaterEnhetPåOppgaver(dataSource: DataSource, msGraphClient: IMsGraphClient) =

    route("/oppdater-enheter").get<Unit, EnhetsoppdateringRapport> {
        val log = LoggerFactory.getLogger("oppdater-enheter")

        val oppgaverUtenEnhet = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaverUtenEnhet()
        }

        val enhetService = EnhetService(msGraphClient)
        oppgaverUtenEnhet.take(100).forEach { oppgaveOgPerson ->
            try {
                dataSource.transaction { connection ->
                    val enhet = enhetService.finnEnhet(oppgaveOgPerson.personIdent)
                    OppgaveRepository(connection).oppdaterEnhet(oppgaveOgPerson.oppgaveId, enhet)
                }
            } catch (e: Exception) {
                log.warn("Fikk feil under prosessering av: $oppgaveOgPerson", e)
            }
        }

        respond(EnhetsoppdateringRapport(oppgaverUtenEnhet.size, oppgaverUtenEnhet.take(100)))
    }
 */