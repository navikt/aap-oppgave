package no.nav.aap.oppgave.mottattdokument

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.oppgave.OppgaveRepository
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger(OppgaveRepository::class.java)

class MottattDokumentRepository(private val connection: DBConnection) {

    fun lagreDokumenter(dokumenter: List<MottattDokument>) {
        val sql = """
                INSERT INTO mottatt_dokument(type, behandling_ref, referanse, opprettet_tidspunkt, opprettet_av)
                VALUES (?, ?, ?, current_timestamp, 'Kelvin')
                ON CONFLICT (referanse) DO NOTHING
            """.trimIndent()

        connection.executeBatch(sql, dokumenter) {
            setParams {
                setEnumName(1, it.type)
                setUUID(2, it.behandlingRef)
                setString(3, it.referanse)
            }
        }
    }

    fun hentUkvitterteDokumenter(behandlingRef: UUID, type: MottattDokumentType): List<MottattDokument> {
        val sql = """
                SELECT * FROM mottatt_dokument 
                WHERE behandling_ref = ? 
                AND type = ?
                AND kvittert_av IS NULL
            """.trimIndent()

        val dokumenter = connection.queryList(sql) {
            setParams {
                setUUID(1, behandlingRef)
                setEnumName(2, type)
            }
            setRowMapper {
                mottattDokumentMapper(it)
            }
        }
        return dokumenter
    }

    fun kvitterDokumenter(behandlingRef: UUID, type: MottattDokumentType, kvittertAv: String) {
        val sql = """
            UPDATE mottatt_dokument SET kvittert_av = ?, kvittert_tidspunkt = current_timestamp 
            WHERE behandling_ref = ? 
            AND type = ?
        """.trimIndent()

        connection.execute(sql) {
            setParams {
                setString(1, kvittertAv)
                setUUID(2, behandlingRef)
                setEnumName(3, type)
            }
        }
    }

    private fun mottattDokumentMapper(row: Row): MottattDokument {
        return MottattDokument(
            type = row.getEnum("type"),
            behandlingRef = row.getUUID("behandling_ref"),
            referanse = row.getString("referanse"),
        )
    }

}