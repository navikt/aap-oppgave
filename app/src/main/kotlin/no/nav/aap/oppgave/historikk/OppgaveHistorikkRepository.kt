package no.nav.aap.oppgave.historikk

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.oppgave.verdityper.Status

class OppgaveHistorikkRepository(private val connection: DBConnection) {

    fun hentHistorikkForOppgave(oppgaveId: Long): List<OppgaveHistorikk> {
        val query = "SELECT * FROM oppgave_historikk WHERE oppgave_id = ? ORDER BY id DESC"

        return connection.queryList(query) {
            setParams {
                setLong(1, oppgaveId)
            }
            setRowMapper { it.mapToHistorikk() }
        }
    }

    private fun Row.mapToHistorikk(): OppgaveHistorikk = OppgaveHistorikk(
        id = getLong("ID"),
        oppgaveId = getLong("OPPGAVE_ID"),
        status = Status.valueOf(getString("STATUS")),
        reservertAv = getStringOrNull("RESERVERT_AV"),
        reservertTidspunkt = getLocalDateTimeOrNull("RESERVERT_TIDSPUNKT"),
        endretAv = getStringOrNull("ENDRET_AV"),
        endretTidspunkt = getLocalDateTimeOrNull("ENDRET_TIDSPUNKT"),
        enhet = getString("ENHET"),
        oppfølgingsenhet = getStringOrNull("OPPFOLGINGSENHET"),
    )

}

