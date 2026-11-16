package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import org.slf4j.LoggerFactory

// Behold jobbtypen for eksisterende database-rader, uten opprydding eller cron.
class FjernOppgaverFraDeaktiverteBrukereJobb : JobbUtfører {
    private val log = LoggerFactory.getLogger(FjernOppgaverFraDeaktiverteBrukereJobb::class.java)

    override fun utfør(input: JobbInput) {
        log.info("Opprydding av oppgaver for deaktiverte brukere er deaktivert.")
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører =
            FjernOppgaverFraDeaktiverteBrukereJobb()

        override fun type() = "oppgave.fjernOppgaverFraDeaktiverteBrukere"
        override fun navn() = "Fjern oppgaver fra deaktiverte brukere"
        override fun beskrivelse() = "Deaktivert jobbtype beholdt for eksisterende database-rader"
    }
}
