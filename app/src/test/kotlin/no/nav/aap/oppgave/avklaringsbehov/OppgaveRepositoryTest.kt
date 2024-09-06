package no.nav.aap.oppgave.avklaringsbehov

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest

class OppgaveRepositoryTest {

    @AfterTest
    fun tearDown() {
        InitTestDatabase.dataSource.transaction { it.execute("DELETE FROM OPPGAVE") }
    }

    @Test
    fun `Opprett oppgave`() {
        opprettNyOppgave()
    }

    @Test
    fun `Avslutt oppgave`() {
        val oppgaveId = opprettNyOppgave()
        InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId)
        }
    }

    @Test
    fun `Reserver neste oppgave finner ingen oppgave fordi opprettet oppgave ikke matcher filter`() {
        opprettNyOppgave(avklaresAv = AvklaresAv.NAVKONTOR)
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(Filter(BehandlingType.FØRSTEGANGSBEHANDLING, AvklaresAv.NAY), "test")
            assertThat(oppgaveId).isNull()
        }
    }

    @Test
    fun `Reserver neste oppgave finner en oppgave fordi en av oppgavene matcher filter`() {
        opprettNyOppgave(avklaresAv = AvklaresAv.NAVKONTOR)
        opprettNyOppgave(avklaresAv = AvklaresAv.NAY)
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(Filter(BehandlingType.FØRSTEGANGSBEHANDLING, AvklaresAv.NAY), "test")
            assertThat(oppgaveId).isNotNull()
        }
    }

    private fun opprettNyOppgave(saksnummer: Saksnummer = Saksnummer("123"), behandlingRef: BehandlingRef = BehandlingRef((UUID.randomUUID())), behandlingType: BehandlingType = BehandlingType.FØRSTEGANGSBEHANDLING, avklaresAv: AvklaresAv = AvklaresAv.NAY): OppgaveId {
        val oppgave = Oppgave(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            behandlingType = behandlingType,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovType = AvklaringsbehovType.AVKLAR_SYKDOM,
            avklaringsbehovStatus = AvklaringsbehovStatus.OPPRETTET,
            avklaresAv = avklaresAv,
            navKontor = if (avklaresAv == AvklaresAv.NAVKONTOR) Navkontor("0100") else null,
            opprettetAv = "bruker1",
            opprettetTidspunkt = LocalDateTime.now()
        )
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgave)
        }
    }
}