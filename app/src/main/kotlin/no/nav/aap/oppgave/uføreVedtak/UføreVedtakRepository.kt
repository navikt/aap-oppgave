package no.nav.aap.oppgave.uføreVedtak

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
            ON CONFLICT (behandling_ref, virkningsdato, status) DO NOTHING;
            """.trimIndent()

        connection.execute(query) {
            setParams {
                setUUID(1, referanse)
                setLocalDate(2, vedtak.virkningsdato)
                setEnumName(3, vedtak.status)
            }
        }
    }

    fun fjernUføreVedtakTag(behandlingsref: UUID, fjernetAv: String){
        val sql = """
            UPDATE UFORE_VEDTAK SET VEDTAK_FJERNET_AV = ?, VEDTAK_FJERNET_TIDSPUNKT = current_timestamp
            WHERE behandling_ref = ?
        """.trimIndent()

        connection.execute(sql) {
            setParams {
                setString(1, fjernetAv)
                setUUID(2, behandlingsref)

            }
        }
    }

    fun hentAktiveUføreVedtakForBehandling(referanse: UUID): UføreVedtak? {
        val query =
            """
            SELECT * FROM UFORE_VEDTAK
            WHERE behandling_ref = ? AND VEDTAK_FJERNET_AV IS NULL
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