package no.nav.aap.oppgave

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.filter.TransientFilterDto
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.fail

class OppgaveRepositoryTest {

    private val dataSource = InitTestDatabase.freshDatabase()

    private val ENHET_NAV_ENEBAKK = "0229"
    private val ENHET_NAV_LØRENSKOG = "0230"
    private val ENHET_NAV_LILLESTRØM = "0231"

    @AfterTest
    fun tearDown() {
        @Suppress("SqlWithoutWhere")
        dataSource.transaction {
            it.execute("DELETE FROM OPPGAVE_HISTORIKK")
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
        dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId, "test")
        }
    }

    @Test
    fun `Finn neste oppgave finner ingen oppgave fordi opprettet oppgave ikke matcher filter`() {
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode("1000"))
        dataSource.transaction { connection ->
            val oppgaver = OppgaveRepository(connection).finnNesteOppgaver(avklaringsbehovFilter("2000"))
            assertThat(oppgaver).hasSize(0)
        }
    }

    @Test
    fun `Finn neste oppgave finner en oppgave fordi en av oppgavene matcher filter`() {
        opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name))
        val oppgaveIdForAvklarStudent = opprettOppgave(avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_STUDENT.kode.name))
        dataSource.transaction { connection ->
            val plukketOppgaver = OppgaveRepository(connection).finnNesteOppgaver(avklaringsbehovFilter(Definisjon.AVKLAR_STUDENT.kode.name))
            assertThat(plukketOppgaver).hasSize(1)
            assertThat(plukketOppgaver.first().oppgaveId).isEqualTo(oppgaveIdForAvklarStudent.id)
        }
    }

    @Test
    fun `Finn neste oppgave som bare matcher på behandlingstype dokumenthåndtering`() {
        opprettOppgave(behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING)
        val oppgaveIdForDokumentshåndteringsoppgave = opprettOppgave(behandlingstype = Behandlingstype.DOKUMENT_HÅNDTERING)

        dataSource.transaction { connection ->
            val plukketOppgaver = OppgaveRepository(connection).finnNesteOppgaver(behandlingstypeFilter(Behandlingstype.DOKUMENT_HÅNDTERING))
            assertThat(plukketOppgaver).hasSize(1)
            assertThat(plukketOppgaver.first().oppgaveId).isEqualTo(oppgaveIdForDokumentshåndteringsoppgave.id)
        }
    }

    @Test
    fun `Finn neste oppgave som bare matcher på behandlingstype journalføring`() {
        opprettOppgave(behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING)
        val oppgaveIdForDokumentshåndteringsoppgave = opprettOppgave(behandlingstype = Behandlingstype.JOURNALFØRING)

        dataSource.transaction { connection ->
            val plukketOppgaver = OppgaveRepository(connection).finnNesteOppgaver(behandlingstypeFilter(Behandlingstype.JOURNALFØRING))
            assertThat(plukketOppgaver).hasSize(1)
            assertThat(plukketOppgaver.first().oppgaveId).isEqualTo(oppgaveIdForDokumentshåndteringsoppgave.id)
        }
    }



    @Test
    fun `Finn neste oppgave finner ikke en oppgave fordi den er avsluttet`() {
        opprettOppgave(status = Status.AVSLUTTET)
        dataSource.transaction { connection ->
            val oppgaver= OppgaveRepository(connection).finnNesteOppgaver(avklaringsbehovFilter())
            assertThat(oppgaver).hasSize(0)
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

        val mineOppgaverFørAvslutt = mineOppgaver("bruker1")
        assertThat(mineOppgaverFørAvslutt).hasSize(3)

        val oppgaveSomSkalAvsluttes = mineOppgaverFørAvslutt.first { it.id == oppgaveId4.id }
        avsluttOppgave(OppgaveId(oppgaveSomSkalAvsluttes.id!!, oppgaveSomSkalAvsluttes.versjon))

        dataSource.transaction { connection ->
            val oppgaver = OppgaveRepository(connection).hentMineOppgaver("bruker1")
            assertThat(oppgaver).hasSize(2)
        }
    }

    @Test
    fun `Avregistrer oppgave`() {
        val oppgaveId = opprettOppgave()

        reserverOppgave(oppgaveId, "saksbehandler1")
        var mineOppgaver  = mineOppgaver("saksbehandler1")
        assertThat(mineOppgaver).hasSize(1)

        avreserverOppgave(OppgaveId(mineOppgaver.first().id!!, mineOppgaver.first().versjon), "saksbehandler1")
        mineOppgaver  = mineOppgaver("saksbehandler1")
        assertThat(mineOppgaver).hasSize(0)
    }

    @Test
    fun `Skal finne oppgaver for angitt veileder`() { opprettOppgave(veileder = "xyz12345")
        val oppgaveId2 = opprettOppgave(veileder = "xyz54321")

        val oppgaver = finnOppgaver(TransientFilterDto(veileder = "xyz54321"))

        assertThat(oppgaver).hasSize(1)
        assertThat(oppgaver.map {it.id}).contains(oppgaveId2.id)
    }

    @Test
    fun `Skal finne oppgaver knyttet til enhet`() {
        opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgaveId2 = opprettOppgave(enhet = ENHET_NAV_LØRENSKOG)
        val oppgaveId3 = opprettOppgave(enhet = ENHET_NAV_LØRENSKOG)

        val oppgaver = finnOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_LØRENSKOG)))

        assertThat(oppgaver).hasSize(2)
        assertThat(oppgaver.map {it.id}[0]).isEqualTo(oppgaveId2.id)
        assertThat(oppgaver.map {it.id}[1]).isEqualTo(oppgaveId3.id)
    }

    @Test
    fun `Kan bruke paging`() {
        val bruker = "user"
        val oppgave1 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgave2 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgave3 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgave4 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)

        val søkUtenPaging = finnOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)))
        assertThat(søkUtenPaging).hasSize(4)

        val søkMedPaging = finnOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)), paging = Paging(1, 1))
        assertThat(søkMedPaging).hasSize(1)

        val søkMedPagingPå10 = finnOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)), paging = Paging(1, 10))
        assertThat(søkMedPagingPå10).hasSize(4)

        reserverOppgave(oppgave1, bruker)
        reserverOppgave(oppgave2, bruker)
        reserverOppgave(oppgave3, bruker)
        reserverOppgave(oppgave4, bruker)

        val mineOppgaver = mineOppgaver(bruker)
        assertThat(mineOppgaver).hasSize(4)

        val mineOppgaverPaged = mineOppgaver(bruker, Paging(1, 2))
        assertThat(mineOppgaverPaged).hasSize(2)
    }

    @Test
    fun `Skal finne oppgaver knyttet til oppfølgingsenhet dersom den er satt`() {
        opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgaveId2 = opprettOppgave(enhet = ENHET_NAV_LØRENSKOG, oppfølgingsenhet = ENHET_NAV_LILLESTRØM)
        opprettOppgave(enhet = ENHET_NAV_LØRENSKOG)

        val oppgaver = finnOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_LILLESTRØM)))

        assertThat(oppgaver).hasSize(1)
        assertThat(oppgaver.map {it.id}).contains(oppgaveId2.id)
    }

    private fun avklaringsbehovFilter(vararg avklaringsbehovKoder: String) =
        FilterDto(1, "Filter for test", "Filter for test", avklaringsbehovKoder = avklaringsbehovKoder.toSet(), opprettetAv = "test", opprettetTidspunkt = LocalDateTime.now())

    private fun behandlingstypeFilter(vararg behandlingstyper: Behandlingstype) =
        FilterDto(1, "Filter for test", "Filter for test", behandlingstyper = behandlingstyper.toSet(), opprettetAv = "test", opprettetTidspunkt = LocalDateTime.now())

    private fun avsluttOppgave(oppgaveId: OppgaveId) {
        dataSource.transaction { connection ->
            OppgaveRepository(connection).avsluttOppgave(oppgaveId, "test")
        }
    }

    private fun reserverOppgave(oppgaveId: OppgaveId, ident: String) {
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverOppgave(oppgaveId, ident, ident)
        }
    }

    private fun avreserverOppgave(oppgaveId: OppgaveId, ident: String) {
        dataSource.transaction { connection ->
            OppgaveRepository(connection).avreserverOppgave(oppgaveId, ident)
        }
    }

    private fun mineOppgaver(ident: String, paging: Paging? = null): List<OppgaveDto> {
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).hentMineOppgaver(ident, paging)
        }
    }

    private fun finnOppgaver(filter: Filter, paging: Paging? = null): List<OppgaveDto> {
        return dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaver(filter, paging = paging)
        }
    }

    private fun opprettOppgave(
        saksnummer: String = "123",
        behandlingRef: UUID = UUID.randomUUID(),
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
        behandlingstype: Behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
        enhet: String = ENHET_NAV_LØRENSKOG,
        oppfølgingsenhet: String? = null,
        veileder: String? = null,
    ): OppgaveId {
        val oppgaveDto = OppgaveDto(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovKode = avklaringsbehovKode.kode,
            status = status,
            behandlingstype = behandlingstype,
            opprettetAv = "bruker1",
            veileder = veileder,
            opprettetTidspunkt = LocalDateTime.now()
        )
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
    }
}