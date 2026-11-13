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
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoGateway
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomApiGateway
import no.nav.aap.oppgave.statistikk.HendelseType
import org.slf4j.LoggerFactory

class FjernOppgaverFraDeaktiverteBrukereJobb(
    private val repository: OppgaveRepository,
    private val flytJobbRepository: FlytJobbRepository,
    private val ansattInfoGateway: AnsattInfoGateway
) : JobbUtfører {
    private val log = LoggerFactory.getLogger(FjernOppgaverFraDeaktiverteBrukereJobb::class.java)

    override fun utfør(input: JobbInput) {

        val oppgaverForBruker = repository.finnÅpneOppgaverSomErReservert()
            .groupBy({ it.ident }, { it })
        if (oppgaverForBruker.isEmpty()) {
            return
        }

        val deaktiverteBrukere = ansattInfoGateway.hentDeaktiverteBrukere(oppgaverForBruker.keys.toList())

        val oppgaverSomMåOppdateres = oppgaverForBruker
            .filterKeys { it in deaktiverteBrukere }
            .flatMap { it.value }

        if (oppgaverSomMåOppdateres.isNotEmpty()) {
            oppgaverSomMåOppdateres.forEach {
                repository.avreserverOppgave(OppgaveId(it.oppgaveId, it.versjon), "KELVIN")
                sendOppgaveStatusOppdatering(
                    oppgaveId = OppgaveId(it.oppgaveId, it.versjon),
                    hendelseType = HendelseType.AVRESERVERT,
                    repository = flytJobbRepository
                )
            }
        }

        log.info("Fjernet ${oppgaverSomMåOppdateres.size} oppgaver for deaktiverte brukere")

    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return FjernOppgaverFraDeaktiverteBrukereJobb(
                OppgaveRepository(connection),
                FlytJobbRepositoryImpl(connection),
                NomApiGateway.withClientCredentialsRestClient()
            )
        }

        override fun type() = "oppgave.fjernOppgaverFraDeaktiverteBrukere"
        override fun navn() = "Fjern oppgaver fra deaktiverte brukere"
        override fun beskrivelse() = "Fjerner oppgaver for brukere som er deaktivert"
        override fun cron(): CronExpression {
            return CronExpression.create("0 0 2 * * *")
        }
    }

}