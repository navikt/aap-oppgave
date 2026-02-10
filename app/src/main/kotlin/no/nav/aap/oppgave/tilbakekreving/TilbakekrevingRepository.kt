package no.nav.aap.oppgave.tilbakekreving

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.verdityper.Beløp

class TilbakekrevingRepository(private val connection: DBConnection) {
    fun lagre(tilbakekrevingVars: TilbakekrevingVars){
        val sql = """
            INSERT INTO TILBAKEKREVING_OPPGAVE_VAR (oppgave_id, belop, tilbakekreving_url)
            VALUES (?, ?, ?)
            ON CONFLICT (oppgave_id) DO UPDATE SET
                belop = EXCLUDED.belop,
                tilbakekreving_url = EXCLUDED.tilbakekreving_url
        """.trimIndent()
        connection.execute(sql, {
            setParams {
                setLong(1, tilbakekrevingVars.oppgaveId)
                setBigDecimal(2, tilbakekrevingVars.beløp)
                setString(3, tilbakekrevingVars.url)
            }
        })
    }

    fun hent(oppgaveId: Long): TilbakekrevingVars {
        val sql = """
            SELECT * FROM TILBAKEKREVING_OPPGAVE_VAR WHERE oppgave_id = ?
        """.trimIndent()
        return connection.queryFirst(sql, {
            setParams {
                setLong(1, oppgaveId)
            }
            setRowMapper {
                TilbakekrevingVars(
                    oppgaveId = it.getLong("oppgave_id"),
                    beløp = it.getBigDecimal("belop"),
                    url = it.getString("TILBAKEKREVING_URL")
                )
            }
        })
    }
}