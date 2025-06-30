package no.nav.aap.oppgave

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.filter.TransientFilterDto
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.liste.UtvidetOppgavelisteFilter
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.fail

class OppgaveRepositoryTest {

    private val dataSource = InitTestDatabase.freshDatabase()

    private val ENHET_NAV_ENEBAKK = "0229"
    private val ENHET_NAV_LØRENSKOG = "0230"
    private val ENHET_NAV_LILLESTRØM = "0231"

    private val VEILEDER_IDENT_1 = "Z999999"
    private val VEILEDER_IDENT_2 = "Z111111"

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
    fun `Skal finne oppgaver for angitt veileder for arbeidsoppfølging`() {
        opprettOppgave(veilederArbeid = VEILEDER_IDENT_2)
        val oppgaveId2 = opprettOppgave(veilederArbeid = VEILEDER_IDENT_1)

        val oppgaver = finnLedigeOppgaver(TransientFilterDto(veileder = VEILEDER_IDENT_1)).oppgaver

        assertThat(oppgaver).hasSize(1)
        assertThat(oppgaver.map {it.id}).contains(oppgaveId2.id)
    }

    @Test
    fun `Skal finne oppgaver for angitt veileder for sykefraværsoppfølging`() {
        opprettOppgave(veilederSykdom = VEILEDER_IDENT_2)
        val oppgaveId2 = opprettOppgave(veilederArbeid = VEILEDER_IDENT_1)

        val oppgaver = finnLedigeOppgaver(TransientFilterDto(veileder = VEILEDER_IDENT_1)).oppgaver

        assertThat(oppgaver).hasSize(1)
        assertThat(oppgaver.map {it.id}).contains(oppgaveId2.id)
    }

    @Test
    fun `Skal finne oppgaver for angitt veileder for både arbeidsoppfølging og sykefraværsoppfølging`() {
        opprettOppgave(veilederSykdom = VEILEDER_IDENT_2)
        val oppgaveId2 = opprettOppgave(veilederArbeid = VEILEDER_IDENT_1)
        val oppgaveId3 = opprettOppgave(veilederSykdom = VEILEDER_IDENT_1)

        val oppgaver = finnLedigeOppgaver(TransientFilterDto(veileder = VEILEDER_IDENT_1)).oppgaver

        assertThat(oppgaver).hasSize(2)
        assertThat(oppgaver.map {it.id}).containsAll(setOf(oppgaveId2.id, oppgaveId3.id))
    }

    @Test
    fun `Skal finne oppgaver basert på saksnummer uavhengig av store og små bokstaver i saksnummeret`() {
        val saksnummerMixCase = "ABC123o"
        val saksnummerUppercase = "ABC123D"
        opprettOppgave(saksnummer = saksnummerMixCase)
        opprettOppgave(saksnummer = saksnummerUppercase)

        assertThat(finnOppgaverForSak(saksnummerMixCase)).hasSize(1)
        assertThat(finnOppgaverForSak(saksnummerMixCase.uppercase())).hasSize(1)
        assertThat(finnOppgaverForSak(saksnummerMixCase.lowercase())).hasSize(1)
        assertThat(finnOppgaverForSak(saksnummerUppercase)).hasSize(1)
        assertThat(finnOppgaverForSak(saksnummerUppercase.uppercase())).hasSize(1)
        assertThat(finnOppgaverForSak(saksnummerUppercase.lowercase())).hasSize(1)
    }

    @Test
    fun `Skal finne oppgaver knyttet til enhet`() {
        opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgaveId2 = opprettOppgave(enhet = ENHET_NAV_LØRENSKOG)
        val oppgaveId3 = opprettOppgave(enhet = ENHET_NAV_LØRENSKOG)

        val oppgaver = finnLedigeOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_LØRENSKOG))).oppgaver

        assertThat(oppgaver).hasSize(2)
        assertThat(oppgaver.map {it.id}[0]).isEqualTo(oppgaveId2.id)
        assertThat(oppgaver.map {it.id}[1]).isEqualTo(oppgaveId3.id)
    }

    @Test
    fun `Kan bruke paging`() {
        val søkUtenTreff = finnLedigeOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_LILLESTRØM)))
        assertThat(søkUtenTreff.oppgaver).hasSize(0)
        assertThat(søkUtenTreff.antallGjenstaaende).isEqualTo(0)

        val bruker = "user"
        val oppgave1 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgave2 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgave3 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgave4 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)

        val søkUtenPaging = finnLedigeOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)))
        assertThat(søkUtenPaging.oppgaver).hasSize(4)
        assertThat(søkUtenPaging.antallGjenstaaende).isEqualTo(0)

        val søkMedPaging = finnLedigeOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)), paging = Paging(1, 1))
        assertThat(søkMedPaging.oppgaver).hasSize(1)
        assertThat(søkMedPaging.antallGjenstaaende).isEqualTo(3)

        val søkMedPagingPå10 = finnLedigeOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)), paging = Paging(1, 10))
        assertThat(søkMedPagingPå10.oppgaver).hasSize(4)
        assertThat(søkMedPagingPå10.antallGjenstaaende).isEqualTo(0)

        val søkMedPagingSomIkkeFinnes = finnLedigeOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)), paging = Paging(2, 25))
        assertThat(søkMedPagingSomIkkeFinnes.oppgaver).hasSize(0)
        assertThat(søkMedPagingSomIkkeFinnes.antallGjenstaaende).isEqualTo(0)

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
    fun `Skal kunne vise ledige oppgaver eller alle oppgaver`() {
        val reservertOppgaveId = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        reserverOppgave(reservertOppgaveId, "saksbehandler")

        val ledigOppgaveId = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)

        val oppgavePåVentId = opprettOppgave(enhet = ENHET_NAV_ENEBAKK,
            påVentTil = LocalDate.now().plusDays(3),
            påVentÅrsak = "årsak",
            venteBegrunnelse = "grunn"
        )

        // skal kun inneholde ledig oppgave
        val ledigeOppgaver = finnLedigeOppgaver(
            filter = TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)),
        )
        assertThat(ledigeOppgaver.oppgaver.first().id == ledigOppgaveId.id)
        assertThat(ledigeOppgaver.oppgaver.map { it.id }.contains(oppgavePåVentId.id)).isFalse()
        assertThat(ledigeOppgaver.oppgaver).hasSize(1)

        // skal inneholde ledig, reservert og på vent
        val alleOppgaver = finnAlleOppgaver(
            filter = TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)),
        )
        assertThat(alleOppgaver.oppgaver).hasSize(3)
        assertThat(reservertOppgaveId.id in alleOppgaver.oppgaver.map { it.id })
        assertThat(oppgavePåVentId.id in alleOppgaver.oppgaver.map { it.id })
        assertThat(ledigOppgaveId.id in alleOppgaver.oppgaver.map { it.id })
    }

    @Test
    fun `Kan bruke utvidet filter`() {
        val oppgave1 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgave2 = opprettOppgave(enhet = ENHET_NAV_ENEBAKK, avklaringsbehovKode = AvklaringsbehovKode("5003"))

        val utvidetFilter = UtvidetOppgavelisteFilter(
            årsaker = setOf(),
            behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING),
            fom = LocalDate.of(2020, 1, 1),
            tom = LocalDate.now().plusDays(1),
            avklaringsbehovKoder = setOf("5003", "12341234")
        )

        val søkMedUtvidetFilter = finnAlleOppgaverMedUtvidetFilter(
            filter = TransientFilterDto(),
            paging = null,
            utvidetOppgavelisteFilter = utvidetFilter,
        )

        val alleOppgaver = finnAlleOppgaver(
            filter = TransientFilterDto(enheter = setOf(ENHET_NAV_ENEBAKK)
            )
        )

        assertThat(alleOppgaver.oppgaver).hasSize(2)
        assertThat(søkMedUtvidetFilter.oppgaver).hasSize(1)
    }

    @Test
    fun `Skal finne oppgaver knyttet til oppfølgingsenhet dersom den er satt`() {
        opprettOppgave(enhet = ENHET_NAV_ENEBAKK)
        val oppgaveId2 = opprettOppgave(enhet = ENHET_NAV_LØRENSKOG, oppfølgingsenhet = ENHET_NAV_LILLESTRØM)
        opprettOppgave(enhet = ENHET_NAV_LØRENSKOG)

        val oppgaver = finnLedigeOppgaver(TransientFilterDto(enheter = setOf(ENHET_NAV_LILLESTRØM))).oppgaver

        assertThat(oppgaver).hasSize(1)
        assertThat(oppgaver.map {it.id}).contains(oppgaveId2.id)
    }

    @Test
    fun `skal markere oppgave med ukvittert legeerklæring`() {
        val oppgaveId = opprettOppgave(harUkvittertLegeerklæring = true)
        val oppgave = dataSource.transaction { connection ->
            OppgaveRepository(connection).hentOppgave(oppgaveId)
        }

        assertThat(oppgave.harUkvittertLegeerklæring).isTrue
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

    private fun finnLedigeOppgaver(filter: Filter, paging: Paging? = null): OppgaveRepository.FinnOppgaverDto {
        return dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaver(filter, paging = paging, kunLedigeOppgaver = true)
        }
    }

    private fun finnAlleOppgaver(filter: Filter, paging: Paging? = null): OppgaveRepository.FinnOppgaverDto {
        return dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaver(filter, paging = paging, kunLedigeOppgaver = false)
        }
    }

    private fun finnAlleOppgaverMedUtvidetFilter(filter: Filter, paging: Paging? = null, utvidetOppgavelisteFilter: UtvidetOppgavelisteFilter): OppgaveRepository.FinnOppgaverDto {
        val kombinertFilter = (filter as TransientFilterDto).copy(
            behandlingstyper = utvidetOppgavelisteFilter.behandlingstyper,
            avklaringsbehovKoder = utvidetOppgavelisteFilter.avklaringsbehovKoder,
        )
        return dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaver(filter = kombinertFilter, paging = paging, kunLedigeOppgaver = false, utvidetFilter = utvidetOppgavelisteFilter)
        }
    }

    private fun finnOppgaverForSak(saksnummer: String): List<OppgaveDto> {
        return dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).finnOppgaverGittSaksnummer(saksnummer)
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
        veilederArbeid: String? = null,
        veilederSykdom: String? = null,
        påVentTil: LocalDate? = null,
        påVentÅrsak: String? = null,
        venteBegrunnelse: String? = null,
        harUkvittertLegeerklæring: Boolean? = null
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
            påVentTil = påVentTil,
            påVentÅrsak = påVentÅrsak,
            venteBegrunnelse = venteBegrunnelse,
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            opprettetTidspunkt = LocalDateTime.now(),
            harUkvittertLegeerklæring = harUkvittertLegeerklæring
        )
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
    }
}