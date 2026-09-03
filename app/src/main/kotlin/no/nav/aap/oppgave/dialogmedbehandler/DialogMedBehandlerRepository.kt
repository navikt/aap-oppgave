package no.nav.aap.oppgave.dialogmedbehandler

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.forespørsel.ForespørselHendelse
import java.util.UUID
import no.nav.aap.oppgave.forespørsel.OpprettForespørselHendelse

class DialogMedBehandlerRepository(private val connection: DBConnection) {

    fun lagreForespørselHendelse(forespørsel: OpprettForespørselHendelse) {
        val sql = """
                INSERT INTO dialog_med_behandler(behandling_ref, type, opprettet_tidspunkt, opprettet_av)
                VALUES (?, ?, current_timestamp, 'Kelvin')
            """.trimIndent()

        connection.execute(sql) {
            setParams {
                setUUID(1, forespørsel.behandlingRef)
                setString(2, forespørsel.type.name)
            }
        }
    }

    fun hentSisteForespørselHendelseForBehandlinger(
        behandlingRefs: Collection<UUID>
    ): Map<UUID, ForespørselHendelse> {
        if (behandlingRefs.isEmpty()) return emptyMap()

        val unikeRefs = behandlingRefs.toSet()
        val placeholders = unikeRefs.joinToString(", ") { "?" }
        val sql = """
                SELECT DISTINCT ON (behandling_ref) behandling_ref, type, opprettet_tidspunkt, opprettet_av FROM dialog_med_behandler 
                WHERE behandling_ref IN ($placeholders)
                ORDER BY behandling_ref, opprettet_tidspunkt DESC
        """.trimIndent()

        return connection.queryList(sql) {
            setParams {
                unikeRefs.forEachIndexed { index, ref ->
                    setUUID(index + 1, ref)
                }
            }
            setRowMapper {
                ForespørselHendelse(
                    behandlingRef = it.getUUID("behandling_ref"),
                    type = it.getEnum("type"),
                    opprettetTidspunkt = it.getLocalDateTime("opprettet_tidspunkt"),
                    opprettetAv = it.getString("opprettet_av"),
                )
            }
        }.associateBy { it.behandlingRef }
    }

}