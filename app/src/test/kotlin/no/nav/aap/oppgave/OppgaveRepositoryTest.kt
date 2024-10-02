package no.nav.aap.oppgave

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.verdityper.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.fail

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
    fun `Opprettelse av duplikat oppgave skal feile`() {
        val uuid = UUID.randomUUID()
        opprettOppgave(saksnummer = "999", behandlingRef = uuid)
        try {
            opprettOppgave(saksnummer = "999", behandlingRef = uuid)
        } catch (e: SQLException) {
            if (e.sqlState != "23505") {
                fail("Skulle mottatt sqlSatte 23505 når det blir forsøkt lagret duplikat oppgave")
            }
        }
    }



    @Test
    fun `Avslutt åpen oppgave`() {
        val oppgaveId = opprettOppgave()
        InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId, "test")
        }
    }

    @Test
    fun `Finn neste oppgave finner ingen oppgave fordi opprettet oppgave ikke matcher filter`() {
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode("1000"))
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).finnNesteOppgave(filter("2000"))
            assertThat(oppgaveId).isNull()
        }
    }

    @Test
    fun `Finn neste oppgave finner en oppgave fordi en av oppgavene matcher filter`() {
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode))
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_STUDENT.kode))
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).finnNesteOppgave(filter(Definisjon.AVKLAR_STUDENT.kode))
            assertThat(oppgaveId).isNotNull()
        }
    }

    @Test
    fun `Finn neste oppgave finner ikke en oppgave fordi den er avsluttet`() {
        opprettOppgave(status = Status.AVSLUTTET)
        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaveId = OppgaveRepository(connection).finnNesteOppgave(filter())
            assertThat(oppgaveId).isNull()
        }
    }

    @Test
    fun `Hent mine reserverte oppgaver som ikke er avsluttet`() {
        val oppgaveId1 = opprettOppgave()
        val oppgaveId2 = opprettOppgave()
        val oppgaveId3 = opprettOppgave()
        val oppgaveId4 = opprettOppgave()

        reserverOppgave(oppgaveId1, "bruker1")
        reserverOppgave(oppgaveId2, "bruker2")
        reserverOppgave(oppgaveId3, "bruker1")
        reserverOppgave(oppgaveId4, "bruker1")
        avsluttOppgave(oppgaveId4)

        InitTestDatabase.dataSource.transaction { connection ->
            val oppgaver = OppgaveRepository(connection).hentMineOppgaver("bruker1")
            assertThat(oppgaver.size).isEqualTo(2)
        }
    }

    @Test
    fun `Avregistrer oppgave`() {
        val oppgaveId = opprettOppgave()

        reserverOppgave(oppgaveId, "saksbehandler1")
        var mineOppgaver  = mineOppgave("saksbehandler1")
        assertThat(mineOppgaver).hasSize(1)

        avreserverOppgave(oppgaveId, "saksbehandler1")
        mineOppgaver  = mineOppgave("saksbehandler1")
        assertThat(mineOppgaver).hasSize(0)
    }


    private fun filter(vararg avklaringsbehovKoder: String) =
        FilterDto(1, "Filter for test", avklaringsbehovKoder.toSet())


    private fun avsluttOppgave(oppgaveId: OppgaveId) {
        InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId, "test")
        }
    }

    private fun reserverOppgave(oppgaveId: OppgaveId, ident: String) {
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverOppgave(oppgaveId, ident, ident)
        }
    }

    private fun avreserverOppgave(oppgaveId: OppgaveId, ident: String) {
        InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).avreserverOppgave(oppgaveId, ident)
        }
    }

    private fun mineOppgave(ident: String): List<OppgaveDto> {
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).hentMineOppgaver(ident)
        }
    }

    private fun opprettOppgave(
        saksnummer: String = "123",
        behandlingRef: UUID = UUID.randomUUID(),
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000")
    ): OppgaveId {
        val oppgaveDto = OppgaveDto(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovKode = avklaringsbehovKode.kode,
            status = status,
            opprettetAv = "bruker1",
            opprettetTidspunkt = LocalDateTime.now()
        )
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
    }
}