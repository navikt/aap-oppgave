package no.nav.aap.oppgave

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.opprett.AvklaringsbehovKode
import no.nav.aap.oppgave.opprett.BehandlingRef
import no.nav.aap.oppgave.opprett.Saksnummer
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
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(filter("2000"), "test")
            assertThat(oppgaveId).isNull()
        }
    }

    @Test
    fun `Reserver neste oppgave finner en oppgave fordi en av oppgavene matcher filter`() {
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode("2000"))
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode("3000"))
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(filter("3000"), "test")
            assertThat(oppgaveId).isNotNull()
        }
    }

    @Test
    fun `Reserver neste oppgave finner ikke en oppgave fordi den er avsluttet`() {
        opprettOppgave(status = Status.AVSLUTTET)
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).reserverNesteOppgave(filter(), "test")
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

    private fun filter(vararg avklaringsbehovKoder: String) =
        Filter(1, "Filter for test", avklaringsbehovKoder.map { AvklaringsbehovKode(it) }.toSet())


    private fun avsluttOppgave(oppgaveId: OppgaveId) {
        InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId)
        }
    }

    private fun reserverOppgave(ident: String): OppgaveId {
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverNesteOppgave(filter(), ident)!!
        }
    }

    private fun opprettOppgave(
        saksnummer: Saksnummer = Saksnummer("123"),
        behandlingRef: BehandlingRef = BehandlingRef((UUID.randomUUID())),
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000")
    ): OppgaveId {
        val oppgave = Oppgave(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovKode = avklaringsbehovKode,
            status = status,
            opprettetAv = "bruker1",
            opprettetTidspunkt = LocalDateTime.now()
        )
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgave)
        }
    }
}