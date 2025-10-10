package no.nav.aap.oppgave.mottattdokument

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import java.util.*

class MottattDokumentRepository(private val connection: DBConnection) {

    fun lagreDokumenter(dokumenter: List<MottattDokument>) {
        val sql = """
                INSERT INTO mottatt_dokument(type, behandling_ref, referanse, opprettet_tidspunkt, opprettet_av)
                VALUES (?, ?, ?, current_timestamp, 'Kelvin')
                ON CONFLICT (referanse) DO NOTHING
            """.trimIndent()

        connection.executeBatch(sql, dokumenter) {
            setParams {
                setString(1, it.type)
                setUUID(2, it.behandlingRef)
                setString(3, it.referanse)
            }
        }
    }

    fun hentUlesteDokumenter(behandlingRef: UUID): List<MottattDokument> {
        val sql = """
                SELECT * FROM mottatt_dokument 
                WHERE behandling_ref = ? 
                AND registrert_lest_av IS NULL
            """.trimIndent()

        val dokumenter = connection.queryList(sql) {
            setParams {
                setUUID(1, behandlingRef)
            }
            setRowMapper {
                mottattDokumentMapper(it)
            }
        }
        return dokumenter
    }

    fun registrerDokumenterSomLest(behandlingRef: UUID, lestAv: String) {
        val sql = """
            UPDATE mottatt_dokument SET registrert_lest_av = ?, registrert_lest_tidspunkt = current_timestamp 
            WHERE behandling_ref = ? 
        """.trimIndent()

        connection.execute(sql) {
            setParams {
                setString(1, lestAv)
                setUUID(2, behandlingRef)
            }
        }
    }

    private fun mottattDokumentMapper(row: Row): MottattDokument {
        return MottattDokument(
            type = row.getString("type"),
            behandlingRef = row.getUUID("behandling_ref"),
            referanse = row.getString("referanse"),
        )
    }

}