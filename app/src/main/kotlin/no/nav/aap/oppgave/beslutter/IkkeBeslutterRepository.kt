package no.nav.aap.oppgave.beslutter

import no.nav.aap.komponenter.dbconnect.DBConnection
import java.util.UUID

class IkkeBeslutterRepository(
    private val connection: DBConnection
) {
    fun lagre(behandlingRef: UUID, identer: List<String>) {
        val query = """
            INSERT INTO IKKE_BESLUTTER(behandling_ref, identer)
            VALUES (?, ?)
            ON CONFLICT (behandling_ref) DO UPDATE SET
                identer = EXCLUDED.identer,
                endret_tidspunkt = current_timestamp
            WHERE IKKE_BESLUTTER.identer IS DISTINCT FROM EXCLUDED.identer
            """.trimIndent()

        connection.execute(query) {
            setParams {
                setUUID(1, behandlingRef)
                setArray(2, identer.distinct().sorted())
            }
        }
    }

    fun hentForBehandlinger(behandlingRefs: Collection<UUID>): Map<UUID, List<String>> {
        if (behandlingRefs.isEmpty()) {
            return emptyMap()
        }
        val query = """
            SELECT behandling_ref, identer FROM IKKE_BESLUTTER
            WHERE behandling_ref = ANY(?::uuid[])
            """.trimIndent()

        return connection.queryList(query) {
            setParams {
                setArray(1, behandlingRefs.distinct().map { it.toString() })
            }
            setRowMapper {
                it.getUUID("behandling_ref") to it.getArray("identer", String::class)
            }
        }.toMap()
    }
}
