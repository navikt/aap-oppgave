package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbType
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.opprettOppgave
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FjernOppgaverFraDeaktiverteBrukereJobbTest {
    @Test
    fun `eksisterende jobb kan leses og utføres uten å endre reservasjoner eller planlegge ny kjøring`() {
        TestDataSource().use { dataSource ->
            JobbType.leggTil(FjernOppgaverFraDeaktiverteBrukereJobb)
            val oppgaveId = opprettOppgave(dataSource = dataSource)

            dataSource.transaction { connection ->
                OppgaveRepository(connection).reserverOppgave(
                    oppgaveId = oppgaveId,
                    reservertAvIdent = "000011111",
                    endretAvIdent = "KELVIN",
                    reservertAvNavn = "Testine Testesen"
                )
                FlytJobbRepository(connection).leggTil(
                    JobbInput(FjernOppgaverFraDeaktiverteBrukereJobb).forSak(oppgaveId.id)
                )
            }

            dataSource.transaction { connection ->
                val oppgaveRepository = OppgaveRepository(connection)
                val før = oppgaveRepository.hentOppgave(oppgaveId.id)
                val jobbRepository = FlytJobbRepository(connection)
                val input = jobbRepository.hentJobberForSak(oppgaveId.id).single()

                assertEquals("oppgave.fjernOppgaverFraDeaktiverteBrukere", input.type())
                assertFalse(input.erScheduledOppgave())
                FjernOppgaverFraDeaktiverteBrukereJobb.konstruer(connection).utfør(input)

                val etter = oppgaveRepository.hentOppgave(oppgaveId.id)
                assertEquals(før.reservertAv, etter.reservertAv)
                assertEquals(før.versjon, etter.versjon)
                assertEquals(1, jobbRepository.hentJobberForSak(oppgaveId.id).size)
            }
        }
    }
}
