package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.cron.CronExpression
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.klienter.pdl.Adressebeskyttelseskode
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlKlient
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.LoggerFactory

class OppdaterOppgaveEnhetJobb(
    private val repository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository
) : JobbUtfører {
    private val log = LoggerFactory.getLogger(OppdaterOppgaveEnhetJobb::class.java)

    override fun utfør(input: JobbInput) {
        val oppgaverForIdent = repository.finnÅpneOppgaverIkkeVikafossen()
            .groupBy({ it.ident }, { it })
        if (oppgaverForIdent.isEmpty()) {
            return
        }

        log.info("Sjekker addressebeskyttelse for ${oppgaverForIdent.keys.size} identer, dette blir ${Math.ceilDiv(oppgaverForIdent.keys.size, 1000)} kall mot PDL.")
        val identerMedStrengtFortroligAdresse = oppgaverForIdent.keys.toList()
            .chunked(1000)
            .flatMap { identBatch ->
                PdlGraphqlKlient.withClientCredentialsRestClient()
                    .hentAdressebeskyttelseForIdenter(identBatch)
            }
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
            repository.oppdaterOppgaveEnhetOgFjernReservasjonBatch(
                oppgaverSomMåOppdateres.map { it.oppgaveId },
                Enhet.NAV_VIKAFOSSEN.kode
            )
        } else 0

        oppgaverSomMåOppdateres.forEach {
            sendOppgaveStatusOppdatering(
                oppgaveId = OppgaveId(
                    id = it.oppgaveId,
                    versjon = it.versjon,
                ),
                hendelseType = HendelseType.OPPDATERT,
                repository = flytJobbRepository
            )
        }

        log.info("Oppdaterte enhet for $antallOppdatert oppgaver")
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return OppdaterOppgaveEnhetJobb(OppgaveRepository(connection), FlytJobbRepositoryImpl(connection))
        }

        override fun type() = "oppgave.oppdaterOppgaveEnhet"
        override fun navn() = "Oppdater oppgaveenhet"
        override fun beskrivelse() = "Flytter oppgaver til korrekt enhet"

        override fun cron(): CronExpression {
            return CronExpression.create("0 0 6 * * *")
        }
    }

}
