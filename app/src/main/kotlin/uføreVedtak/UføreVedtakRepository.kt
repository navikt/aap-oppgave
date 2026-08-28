package uføreVedtak

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import java.util.UUID

class UføreVedtakRepository (
    private val connection: DBConnection
) {
    fun lagreUføreVedtak(
        referanse: UUID,
        vedtak: UføreVedtak
    ) {
        val query =
            """
            INSERT INTO UFORE_VEDTAK(behandling_ref, virkningsdato, status)
            VALUES (?, ?, ?)
            """.trimIndent()

        connection.execute(query) {
            setParams {
                setUUID(1, referanse)
                setLocalDate(2, vedtak.virkningsdato)
                setEnumName(3, vedtak.status)
            }
        }
    }

    fun hentUføreVedtakForBehandling(referanse: UUID): UføreVedtak? {
        val query =
            """
            SELECT * FROM UFORE_VEDTAK
            WHERE behandling_ref = ?
            ORDER BY virkningsdato DESC
            """.trimIndent()

       return connection.queryList(query) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper {
                uføreVedtakMapper(it)
            }
        }.firstOrNull()
    }

    private fun uføreVedtakMapper(row: Row) : UføreVedtak =
        UføreVedtak(
            virkningsdato = row.getLocalDate("virkningsdato"),
            status = row.getEnum("status"),
        )

}