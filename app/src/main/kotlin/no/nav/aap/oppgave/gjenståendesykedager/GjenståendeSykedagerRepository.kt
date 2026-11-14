package no.nav.aap.oppgave.gjenståendesykedager

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import java.util.UUID

class GjenståendeSykedagerRepository(
    private val connection: DBConnection
) {
    fun lagre(behandlingRef: UUID, gjenståendeSykedager: GjenståendeSykedager) {
        val query = """
            INSERT INTO GJENSTAENDE_SYKEDAGER(behandling_ref, grunnlag, gjenstaende_dager, vurdert_til_og_med, maksdato)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (behandling_ref) DO UPDATE SET
                grunnlag = EXCLUDED.grunnlag,
                gjenstaende_dager = EXCLUDED.gjenstaende_dager,
                vurdert_til_og_med = EXCLUDED.vurdert_til_og_med,
                maksdato = EXCLUDED.maksdato,
                endret_tidspunkt = current_timestamp
            WHERE (GJENSTAENDE_SYKEDAGER.grunnlag, GJENSTAENDE_SYKEDAGER.gjenstaende_dager,
                   GJENSTAENDE_SYKEDAGER.vurdert_til_og_med, GJENSTAENDE_SYKEDAGER.maksdato)
                IS DISTINCT FROM (EXCLUDED.grunnlag, EXCLUDED.gjenstaende_dager,
                                  EXCLUDED.vurdert_til_og_med, EXCLUDED.maksdato)
            """.trimIndent()

        connection.execute(query) {
            setParams {
                setUUID(1, behandlingRef)
                setEnumName(2, gjenståendeSykedager.grunnlag)
                setInt(3, gjenståendeSykedager.gjenståendeDager)
                setLocalDate(4, gjenståendeSykedager.vurdertTilOgMed)
                setLocalDate(5, gjenståendeSykedager.maksdato)
            }
        }
    }

    fun hent(behandlingRef: UUID): GjenståendeSykedager? =
        hentForBehandlinger(listOf(behandlingRef))[behandlingRef]

    fun hentForBehandlinger(behandlingRefs: Collection<UUID>): Map<UUID, GjenståendeSykedager> {
        if (behandlingRefs.isEmpty()) {
            return emptyMap()
        }
        val query = """
            SELECT * FROM GJENSTAENDE_SYKEDAGER
            WHERE behandling_ref = ANY(?::uuid[])
            """.trimIndent()

        return connection.queryList(query) {
            setParams {
                setArray(1, behandlingRefs.distinct().map { it.toString() })
            }
            setRowMapper {
                it.getUUID("behandling_ref") to gjenståendeSykedagerMapper(it)
            }
        }.toMap()
    }

    private fun gjenståendeSykedagerMapper(row: Row) = GjenståendeSykedager(
        grunnlag = row.getEnum("grunnlag"),
        gjenståendeDager = row.getIntOrNull("gjenstaende_dager"),
        vurdertTilOgMed = row.getLocalDateOrNull("vurdert_til_og_med"),
        maksdato = row.getLocalDateOrNull("maksdato"),
    )
}
