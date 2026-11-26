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
            WHERE behandling_ref = ? AND VEDTAK_FJERNET_AV IS NULL
        """.trimIndent()

        connection.execute(sql) {
            setParams {
                setString(1, fjernetAv)
                setUUID(2, behandlingsref)
            }
        }
    }

    fun hentAktiveUføreVedtakForBehandling(referanse: UUID): UføreVedtak? =
        hentAktiveUføreVedtakForBehandlinger(listOf(referanse))[referanse]

    fun hentAktiveUføreVedtakForBehandlinger(referanser: Collection<UUID>): Map<UUID, UføreVedtak> {
        if (referanser.isEmpty()) {
            return emptyMap()
        }
        val query =
            """
            SELECT DISTINCT ON (behandling_ref) *
            FROM UFORE_VEDTAK
            WHERE behandling_ref = ANY(?::uuid[]) AND VEDTAK_FJERNET_AV IS NULL
            ORDER BY behandling_ref, virkningsdato DESC
            """.trimIndent()

        return connection.queryList(query) {
            setParams {
                setArray(1, referanser.distinct().map { it.toString() })
            }
            setRowMapper {
                it.getUUID("behandling_ref") to uføreVedtakMapper(it)
            }
        }.toMap()
    }

    private fun uføreVedtakMapper(row: Row) : UføreVedtak =
        UføreVedtak(
            referanse = row.getUUID("behandling_ref"),
            virkningsdato = row.getLocalDate("virkningsdato"),
            status = row.getEnum("status"),
        )

}