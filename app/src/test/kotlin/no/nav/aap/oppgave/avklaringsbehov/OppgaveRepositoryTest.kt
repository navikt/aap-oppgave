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
        InitTestDatabase.dataSource.transaction {
            @Suppress("SqlWithoutWhere")
            it.execute("DELETE FROM OPPGAVE")
        }
    }

    @Test
    fun `Opprett ny oppgave`() {
        opprettOppgave()
    }

    @Test
    fun `Avslutt åpen oppgave`() {
        val oppgaveId = opprettOppgave()
        InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId)
        }
    }

    @Test
    fun `Reserver neste oppgave finner ingen oppgave fordi opprettet oppgave ikke matcher filter`() {
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode("1000"))
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(Filter(setOf(AvklaringsbehovKode("2000"))), "test")
            assertThat(oppgaveId).isNull()
        }
    }

    @Test
    fun `Reserver neste oppgave finner en oppgave fordi en av oppgavene matcher filter`() {
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode("2000"))
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode("3000"))
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(Filter(setOf(AvklaringsbehovKode("3000"))), "test")
            assertThat(oppgaveId).isNotNull()
        }
    }

    @Test
    fun `Reserver neste oppgave finner ikke en oppgave fordi den er avsluttet`() {
        opprettOppgave(oppgaveStatus = OppgaveStatus.AVSLUTTET)
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(Filter(), "test")
            assertThat(oppgaveId).isNull()
        }
    }

    @Test
    fun `Hent mine åpne oppgaver`() {
        opprettOppgave()
        opprettOppgave()
        opprettOppgave()
        opprettOppgave()

        reserverOppgave("bruker1")
        reserverOppgave("bruker2")
        reserverOppgave("bruker1")
        val oppgaveId = reserverOppgave("bruker1")
        avsluttOppgave(oppgaveId)

        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaver = OppgaveRepository(connection).hentMineOppgaver("bruker1")
            assertThat(oppgaver.size).isEqualTo(2)
        }
    }

    private fun avsluttOppgave(oppgaveId: OppgaveId) {
        InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId)
        }
    }

    private fun reserverOppgave(ident: String): OppgaveId {
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverNesteOppgave(Filter(), ident)!!
        }
    }

    private fun opprettOppgave(
        saksnummer: Saksnummer = Saksnummer("123"),
        behandlingRef: BehandlingRef = BehandlingRef((UUID.randomUUID())),
        oppgaveStatus: OppgaveStatus = OppgaveStatus.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000")
    ): OppgaveId {
        val oppgave = Oppgave(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovKode = avklaringsbehovKode,
            oppgaveStatus = oppgaveStatus,
            opprettetAv = "bruker1",
            opprettetTidspunkt = LocalDateTime.now()
        )
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgave)
        }
    }
}