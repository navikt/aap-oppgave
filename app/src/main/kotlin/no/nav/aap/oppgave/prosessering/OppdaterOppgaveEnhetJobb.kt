package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.cron.CronExpression
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient
import org.slf4j.LoggerFactory

const val NAV_VIKAFOSSEN = "2103"

class OppdaterOppgaveEnhetJobb(private val repository: OppgaveRepository) : JobbUtfører {
    private val log = LoggerFactory.getLogger(OppdaterOppgaveEnhetJobb::class.java)
    
    override fun utfør(input: JobbInput) {
        val oppgaverForIdent = repository.finnÅpneOppgaverIkkeVikafossen()
            .groupBy({ it.ident }, { it.oppgaveId })
        if (oppgaverForIdent.isEmpty()) {
            return
        }
        
        val identerMedStrengtFortroligAdresse = PdlGraphqlKlient.withClientCredentialsRestClient()
            .hentAdressebeskyttelseForIdenter(oppgaverForIdent.keys.toList())
            .filter {
                it.person!!.adressebeskyttelse!!
                    .any { adressebeskyttelse ->
                        adressebeskyttelse.gradering == Adressebeskyttelseskode.STRENGT_FORTROLIG
                    }
            }
            .map { it.ident }

        val oppgaverSomMåOppdateres = oppgaverForIdent
            .filterKeys { identerMedStrengtFortroligAdresse.contains(it) }
            .flatMap { it.value }

        val antallOppdatert = if (oppgaverSomMåOppdateres.isNotEmpty()) {
            repository.oppdaterOppgaveEnhetOgFjernReservasjonBatch(oppgaverSomMåOppdateres, NAV_VIKAFOSSEN)
        } else 0

        log.info("Oppdaterte enhet for $antallOppdatert oppgaver")
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return OppdaterOppgaveEnhetJobb(OppgaveRepository(connection))
        }

        override fun type() = "oppgave.oppdaterOppgaveEnhet"
        override fun navn() = "Oppdater oppgaveenhet"
        override fun beskrivelse() = "Flytter oppgaver til korrekt enhet"

        override fun cron(): CronExpression {
            return CronExpression.create("0 0 6 * * *")
        }
    }

}