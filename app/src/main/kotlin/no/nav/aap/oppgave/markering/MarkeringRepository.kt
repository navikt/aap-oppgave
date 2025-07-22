package no.nav.aap.oppgave.markering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.oppgave.BehandlingMarkering
import java.util.UUID

class MarkeringRepository(private val connection: DBConnection) {

    fun oppdaterMarkeringerForBehandling(referanse: UUID, markeringer: List<BehandlingMarkering>) {
        // fjern gamle og opprett nye markeringer
        slettMarkeringerForBehandling(referanse)
        if (markeringer.isNotEmpty()) {
            lagreMarkeringerForBehandling(referanse, markeringer)
        }
    }

    fun lagreMarkeringerForBehandling(referanse: UUID, markeringer: List<BehandlingMarkering>) {
        val query = """
            INSERT INTO MARKERING (behandling_ref, markering_type, begrunnelse, opprettet_av)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        connection.executeBatch(query, markeringer) {
            setParams {
                setUUID(1, referanse)
                setEnumName(2, it.markeringType)
                setString(3, it.begrunnelse)
                setString(4, it.opprettetAv)
            }
        }
    }

    fun hentMarkeringerForBehandling(referanse: UUID): List<BehandlingMarkering> {
        val query = """
            SELECT * FROM MARKERING WHERE behandling_ref = ?
        """.trimIndent()

        val markeringer = connection.queryList(query) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                markeringMapper(it)
            }
        }
        return markeringer
    }

    private fun slettMarkeringerForBehandling(referanse: UUID) {
        val query = """
            DELETE FROM MARKERING WHERE behandling_ref = ?
        """.trimIndent()

        connection.execute(query) {
            setParams { setUUID(1, referanse) }
        }
    }

    private fun markeringMapper(row: Row): BehandlingMarkering {
        return BehandlingMarkering(
            markeringType = row.getEnum("markering_type"),
            begrunnelse = row.getString("begrunnelse"),
            opprettetAv = row.getString("opprettet_av"),
        )
    }
}
