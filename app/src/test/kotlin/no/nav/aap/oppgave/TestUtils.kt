package no.nav.aap.oppgave

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.oppdater.OpprettOppgave
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

const val ENHET_NAV_LØRENSKOG = "0230"

fun EmbeddedServer<*, *>.port(): Int =
    runBlocking { this@port.engine.resolvedConnectors() }
        .first { it.type == ConnectorType.HTTP }
        .port


fun opprettOppgave(
    personIdent: String = "12345678901",
    saksnummer: String = "123",
    behandlingRef: UUID = UUID.randomUUID(),
    status: Status = Status.OPPRETTET,
    avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
    behandlingstype: Behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
    enhet: String = ENHET_NAV_LØRENSKOG,
    oppfølgingsenhet: String? = null,
    veilederArbeid: String? = null,
    veilederSykdom: String? = null,
    behandlingOpprettet: LocalDateTime = LocalDateTime.now(),
    harUlesteDokumenter: Boolean = false,
    påVentTil: LocalDate? = null,
    påVentÅrsak: String? = null,
    venteBegrunnelse: String? = null,
    returInformasjon: ReturInfo? = null,
    dataSource: DataSource,
): OppgaveId {
    val oppgave = OpprettOppgave(
        personIdent = personIdent,
        saksnummer = saksnummer,
        journalpostId = null,
        behandlingRef = behandlingRef,
        enhet = enhet,
        oppfølgingsenhet = oppfølgingsenhet,
        behandlingOpprettet = behandlingOpprettet,
        avklaringsbehovKode = avklaringsbehovKode.kode,
        status = status,
        behandlingstype = behandlingstype,
        opprettetAv = "bruker1",
        veilederArbeid = veilederArbeid,
        veilederSykdom = veilederSykdom,
        opprettetTidspunkt = LocalDateTime.now(),
        harFortroligAdresse = false,
        erSkjermet = false,
        harUlesteDokumenter = harUlesteDokumenter,
        påVentTil = påVentTil,
        påVentÅrsak = påVentÅrsak,
        venteBegrunnelse = venteBegrunnelse,
        returInformasjon = returInformasjon
    )
    return dataSource.transaction { connection ->
        OppgaveRepository(connection).opprettOppgave(oppgave)
    }
}