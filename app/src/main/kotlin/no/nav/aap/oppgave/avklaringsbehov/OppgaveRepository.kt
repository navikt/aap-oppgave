package no.nav.aap.oppgave.avklaringsbehov

import no.nav.aap.komponenter.dbconnect.DBConnection
import java.time.LocalDateTime

class OppgaveRepository(private val connection: DBConnection) {

    fun opprettOppgave(oppgave: Oppgave): OppgaveId {
        val query = """
            INSERT INTO OPPGAVE (
                SAKSNUMMER,
                BEHANDLING_REF,
                BEHANDLING_TYPE,
                BEHANDLING_OPPRETTET,
                AVKLARINGSBEHOV_TYPE,
                AVKLARINGSBEHOV_STATUS,
                AVKLARES_AV,
                NAVKONTOR,
                OPPRETTET_AV,
                OPPRETTET_TIDSPUNKT
            ) VALUES (
                ?, ?, ?, ?, ? ,? ,? ,?, ?, ?
            )
            
        """.trimIndent()
        val id = connection.executeReturnKey(query) {
            setParams {
                setString(1, oppgave.saksnummer.toString())
                setUUID(2, oppgave.behandlingRef.uuid)
                setString(3, oppgave.behandlingType.name)
                setLocalDateTime(4, oppgave.behandlingOpprettet)
                setString(5, oppgave.avklaringsbehovType.kode)
                setString(6, oppgave.avklaringsbehovStatus.name)
                setString(7, oppgave.avklaresAv.name)
                setString(8, oppgave.navKontor?.kommunenr)
                setString(9, oppgave.opprettetAv)
                setLocalDateTime(10, LocalDateTime.now())
            }
        }
        return OppgaveId(id)
    }

    fun avsluttOppgave(oppgaveId: OppgaveId) {
        val query = """
            UPDATE OPPGAVE SET AVKLARINGSBEHOV_STATUS = 'AVSLUTTET' WHERE ID = ?
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setLong(1, oppgaveId.id)
            }
        }
    }

    fun reserverNesteOppgave(filter: Filter, ident: String): OppgaveId? {
        val hentNesteOppgaveQuery = """
            SELECT 
                ID
            FROM 
                OPPGAVE 
            WHERE 
                ${filter.whereClause()} AND RESERVERT_AV IS NULL AND AVKLARINGSBEHOV_STATUS != 'AVSLUTTET'
            ORDER BY BEHANDLING_OPPRETTET
            LIMIT 1
            FOR UPDATE
            SKIP LOCKED
        """.trimIndent()

        val oppgaveId = connection.queryFirstOrNull(hentNesteOppgaveQuery) { setRowMapper { it.getLong("id") } }

        if (oppgaveId != null) {
           return reserverOppgave(connection, OppgaveId(oppgaveId), ident)
        }
        return null
    }

    fun hentMineOppgaver(ident: String): List<Oppgave> {
        val query = """
            SELECT 
                ID,
                SAKSNUMMER,
                BEHANDLING_REF,
                BEHANDLING_TYPE,
                BEHANDLING_OPPRETTET,
                AVKLARINGSBEHOV_TYPE,
                AVKLARINGSBEHOV_STATUS,
                AVKLARES_AV,
                NAVKONTOR,
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
                AVKLARINGSBEHOV_STATUS != 'AVSLUTTET'
        """.trimIndent()

        return connection.queryList<Oppgave>(query) {
            setParams {
                setString(1, ident)
            }
            setRowMapper { row ->
                Oppgave (
                    id = OppgaveId(row.getLong("ID")),
                    saksnummer = Saksnummer(row.getString("SAKSNUMMER")),
                    behandlingRef = BehandlingRef(row.getUUID("BEHANDLING_REF")),
                    behandlingType = BehandlingType.valueOf(row.getString("BEHANDLING_TYPE")),
                    behandlingOpprettet = row.getLocalDateTime("BEHANDLING_OPPRETTET"),
                    avklaringsbehovType = AvklaringsbehovType.entries.first { row.getString("AVKLARINGSBEHOV_TYPE") == it.kode },
                    avklaringsbehovStatus = AvklaringsbehovStatus.valueOf(row.getString("AVKLARINGSBEHOV_STATUS")),
                    avklaresAv = AvklaresAv.valueOf(row.getString("AVKLARES_AV")),
                    navKontor = tilNavkontor(row.getStringOrNull("NAVKONTOR")),
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

    private fun tilNavkontor(kode: String?) = if (kode != null) Navkontor(kode) else null

    private fun reserverOppgave(connection: DBConnection, oppgaveId: OppgaveId, ident: String): OppgaveId {
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
        return oppgaveId
    }

    private fun Filter.whereClause(): String {
        val sb = StringBuilder()
        sb.append("BEHANDLING_TYPE = '${behandlingType.name}' AND AVKLARES_AV = '${avklaresAv.name}'")
        if (AvklaresAv.NAVKONTOR == avklaresAv) {
            sb.append(" AND NAVKONTOR = '$navkontor'")
        }
        if (avklaringsbehovTyper.isNotEmpty()) {
            sb.append(" AND AVKLARINGSBEHOV_TYPE = '${avklaringsbehovTyper.tilStringListe()}'")
        }
        return " $sb "
    }

    private fun Set<AvklaringsbehovType>.tilStringListe(): String {
        return map {"'${it.kode}'"}.joinToString(prefix = "(", postfix = ")", separator = ", ")
    }
}