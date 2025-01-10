package no.nav.aap.oppgave.enhet

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.httpklient.auth.token
import no.nav.aap.oppgave.OppgaveOgPerson
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.norg.NorgKlient
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppdater.OppdaterOppgaveService
import no.nav.aap.oppgave.server.authenticate.ident
import javax.sql.DataSource


fun NormalOpenAPIRoute.hentEnhetApi(msGraphClient: IMsGraphClient, prometheus: PrometheusMeterRegistry) =

    route("/enheter").get<Unit, List<EnhetDto>> {
        prometheus.httpCallCounter("/enheter").increment()
        val enheter = EnhetService(msGraphClient).hentEnheter(token().token(), ident())
        val enhetNrTilNavn = NorgKlient().hentEnheter()
        val enheterMedNavn = enheter.map { EnhetDto(it, enhetNrTilNavn[it] ?: "") }
        respond(enheterMedNavn)
    }

data class EnhetsoppdateringRapport(val antallOppgaverUtenEnhet: Int, val oppgaveOgPersonListe: List<OppgaveOgPerson>)

fun NormalOpenAPIRoute.oppdaterEnhetPåOppgaver(dataSource: DataSource, msGraphClient: IMsGraphClient) =

    route("/oppdater-enheter").get<Unit, EnhetsoppdateringRapport> {

        val oppgaverUtenEnhet = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaverUtenEnhet()
        }

        val enhetService = EnhetService(msGraphClient)
        oppgaverUtenEnhet.take(100).forEach { oppgaveOgPerson ->
            dataSource.transaction { connection ->
                val enhet = enhetService.finnEnhet(oppgaveOgPerson.personIdent)
                OppgaveRepository(connection).oppdaterEnhet(oppgaveOgPerson.oppgaveId, enhet)
            }
        }

        respond(EnhetsoppdateringRapport(oppgaverUtenEnhet.size, oppgaverUtenEnhet.take(100)))
    }