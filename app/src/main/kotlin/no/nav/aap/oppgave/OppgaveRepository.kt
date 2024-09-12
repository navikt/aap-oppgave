package no.nav.aap.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.verdityper.AvklaringsbehovType
import no.nav.aap.oppgave.verdityper.OppgaveId
import no.nav.aap.oppgave.verdityper.Status
import java.util.UUID

class OppgaveRepository(private val connection: DBConnection) {

    fun opprettOppgave(oppgaveDto: OppgaveDto): OppgaveId {
        val query = """
            INSERT INTO OPPGAVE (
                SAKSNUMMER,
                BEHANDLING_REF,
                JOURNALPOST_ID,
                BEHANDLING_OPPRETTET,
                AVKLARINGSBEHOV_TYPE,
                STATUS,
                OPPRETTET_AV,
                OPPRETTET_TIDSPUNKT
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?
            )
            
        """.trimIndent()
        val id = connection.executeReturnKey(query) {
            setParams {
                setString(1, oppgaveDto.saksnummer)
                setUUID(2, oppgaveDto.behandlingRef)
                setLong(3, oppgaveDto.journalpostId)
                setLocalDateTime(4, oppgaveDto.behandlingOpprettet)
                setString(5, oppgaveDto.avklaringsbehovType.kode)
                setString(6, oppgaveDto.status.name)
                setString(7, oppgaveDto.opprettetAv)
                setLocalDateTime(8, oppgaveDto.opprettetTidspunkt)
            }
        }
        return OppgaveId(id)
    }

    fun avsluttOppgave(oppgaveId: OppgaveId) {
        val query = """
            UPDATE OPPGAVE SET STATUS = 'AVSLUTTET' WHERE ID = ?
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setLong(1, oppgaveId.id)
            }
        }
    }

    fun reserverNesteOppgave(filterDto: FilterDto, ident: String): NesteOppgaveDto? {
        val hentNesteOppgaveQuery = """
            SELECT 
                ID, SAKSNUMMER, BEHANDLING_REF, JOURNALPOST_ID
            FROM 
                OPPGAVE 
            WHERE 
                ${filterDto.whereClause()} RESERVERT_AV IS NULL AND STATUS != 'AVSLUTTET'
            ORDER BY BEHANDLING_OPPRETTET
            LIMIT 1
            FOR UPDATE
            SKIP LOCKED
        """.trimIndent()

        val nesteOppgave = connection.queryFirstOrNull(hentNesteOppgaveQuery) {
            setRowMapper {
                NesteOppgaveDto(
                    oppgaveId = OppgaveId(it.getLong("ID")),
                    saksnummer = it.getStringOrNull("SAKSNUMMER"),
                    behandlingRef = it.getUUIDOrNull("BEHANDLING_REF"),
                    journalpostId = it.getLongOrNull("JOURNALPOST_ID"),
                )
            }
        }

        if (nesteOppgave != null) {
           reserverOppgave(connection, nesteOppgave.oppgaveId, ident)
        }
        return nesteOppgave
    }

    fun hentMineOppgaver(ident: String): List<OppgaveDto> {
        val query = """
            SELECT 
                ID,
                SAKSNUMMER,
                BEHANDLING_REF,
                JOURNALPOST_ID,
                BEHANDLING_OPPRETTET,
                AVKLARINGSBEHOV_TYPE,
                STATUS,
                RESERVERT_AV,
                RESERVERT_TIDSPUNKT,
                OPPRETTET_AV,
                OPPRETTET_TIDSPUNKT,
                ENDRET_AV,
                ENDRET_TIDSPUNKT
            FROM
                OPPGAVE    
            WHERE
                RESERVERT_AV = ? AND
                STATUS != 'AVSLUTTET'
        """.trimIndent()

        return connection.queryList<OppgaveDto>(query) {
            setParams {
                setString(1, ident)
            }
            setRowMapper { row ->
                OppgaveDto(
                    id = OppgaveId(row.getLong("ID")),
                    saksnummer = row.getStringOrNull("SAKSNUMMER"),
                    behandlingRef = row.getUUIDOrNull("BEHANDLING_REF"),
                    journalpostId = row.getLongOrNull("JOURNALPOST_ID"),
                    behandlingOpprettet = row.getLocalDateTime("BEHANDLING_OPPRETTET"),
                    avklaringsbehovType = AvklaringsbehovType(row.getString("AVKLARINGSBEHOV_TYPE")),
                    status = Status.valueOf(row.getString("STATUS")),
                    reservertAv = row.getStringOrNull("RESERVERT_AV"),
                    reservertTidspunkt = row.getLocalDateTimeOrNull("RESERVERT_TIDSPUNKT"),
                    opprettetAv = row.getString("OPPRETTET_AV"),
                    opprettetTidspunkt = row.getLocalDateTime("OPPRETTET_TIDSPUNKT"),
                    endretAv = row.getStringOrNull("ENDRET_AV"),
                    endretTidspunkt = row.getLocalDateTimeOrNull("ENDRET_TIDSPUNKT")
                )
            }
        }
    }

    fun hentOppgaverForReferanse(saksnummer: String?, behandlingRef: UUID?, journalpostId: Long?, avklaringsbehovType: AvklaringsbehovType, ident: String): List<OppgaveId> {
        val oppgaverForReferanseQuery = """
            SELECT 
                OPPGAVE_ID 
            FROM 
                OPPGAVE 
            WHERE 
                SAKSNUMMER = ? AND
                BEHANDLING_REF = ? AND
                JOURNALPOST_ID = ? AND
                AVKLARINGSBEHOV_TYPE = ? AND
                RESERVERT_AV = ?
        """.trimIndent()

        return connection.queryList<OppgaveId>(oppgaverForReferanseQuery) {
            setParams {
                setString(1, saksnummer)
                setUUID(2, behandlingRef)
                setLong(3, journalpostId)
                setString(4, avklaringsbehovType.kode)
                setString(5, ident)
            }
        }
    }

    private fun reserverOppgave(connection: DBConnection, oppgaveId: OppgaveId, ident: String) {
        val updaterOppgaveReservasjonQuery = """
            UPDATE 
                OPPGAVE 
            SET 
                RESERVERT_AV = ?,
                RESERVERT_TIDSPUNKT = CURRENT_TIMESTAMP,
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP
            WHERE ID = ?
        """.trimIndent()

        connection.execute(updaterOppgaveReservasjonQuery) {
            setParams {
                setString(1, ident)
                setString(2, ident)
                setLong(3, oppgaveId.id)
            }
        }
    }

    private fun FilterDto.whereClause(): String {
        if (avklaringsbehovKoder.isNotEmpty()) {
            return "AVKLARINGSBEHOV_TYPE IN ${avklaringsbehovKoder.tilStringListe()} AND "
        }
        return ""
    }

    private fun Set<AvklaringsbehovType>.tilStringListe(): String {
        return map {"'${it.kode}'"}.joinToString(prefix = "(", postfix = ")", separator = ", ")
    }
}