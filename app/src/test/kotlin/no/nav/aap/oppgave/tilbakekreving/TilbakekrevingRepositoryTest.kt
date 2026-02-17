package no.nav.aap.oppgave.tilbakekreving

import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.ReturInformasjon
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class TilbakekrevingRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()


    @Test
    fun `kan lagre og hente tilbakekrevings vars`() {
        dataSource.transaction { connection ->
            val oppgave = opprettOppgave(behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING)
            val vars = TilbakekrevingVars(
                oppgaveId = oppgave.id,
                beløp = BigDecimal(1000.00),
                url = "http://tilbakekreving.nav.no/oppgave/12345"
            )


            val repository = TilbakekrevingRepository(connection)

            repository.lagre(vars)

            val hentetVars = repository.hent(vars.oppgaveId)

            assertNotNull(hentetVars)
            assertEquals(vars.oppgaveId, hentetVars.oppgaveId)
            assertEquals(vars.beløp.toBigInteger(), hentetVars.beløp.toBigInteger())
            assertEquals(vars.url, hentetVars.url)
        }
    }

    private fun opprettOppgave(
        saksnummer: String = "123",
        behandlingRef: UUID = UUID.randomUUID(),
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
        behandlingstype: Behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
        enhet: String = Enhet.NAV_KINN.toString(),
        oppfølgingsenhet: String? = null,
        veilederArbeid: String? = null,
        veilederSykdom: String? = null,
        påVentTil: LocalDate? = null,
        påVentÅrsak: String? = null,
        venteBegrunnelse: String? = null,
        harUlesteDokumenter: Boolean = false,
        returInformasjon: ReturInformasjon? = null,
        årsakTilOpprettelse: String? = "SØKNAD",
        behandlingOpprettet: LocalDateTime =LocalDateTime.now().minusDays(3)
    ): OppgaveId {
        val oppgaveDto = OppgaveDto(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            behandlingOpprettet = behandlingOpprettet,
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
            harUlesteDokumenter = harUlesteDokumenter,
            returInformasjon = returInformasjon,
            årsakTilOpprettelse = årsakTilOpprettelse
        )
        return dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
    }

}