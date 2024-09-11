package no.nav.aap.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.Status
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.opprett.AvklaringsbehovKode
import no.nav.aap.oppgave.opprett.BehandlingRef
import no.nav.aap.oppgave.opprett.Saksnummer

class OppgaveRepository(private val connection: DBConnection) {

    fun opprettOppgave(oppgave: Oppgave): OppgaveId {
        val query = """
            INSERT INTO OPPGAVE (
                SAKSNUMMER,
                BEHANDLING_REF,
                BEHANDLING_OPPRETTET,
                AVKLARINGSBEHOV_KODE,
                STATUS,
                OPPRETTET_AV,
                OPPRETTET_TIDSPUNKT
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?
            )
            
        """.trimIndent()
        val id = connection.executeReturnKey(query) {
            setParams {
                setString(1, oppgave.saksnummer.toString())
                setUUID(2, oppgave.behandlingRef.uuid)
                setLocalDateTime(3, oppgave.behandlingOpprettet)
                setString(4, oppgave.avklaringsbehovKode.kode)
                setString(5, oppgave.status.name)
                setString(6, oppgave.opprettetAv)
                setLocalDateTime(7, oppgave.opprettetTidspunkt)
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

    fun reserverNesteOppgave(filter: Filter, ident: String): OppgaveId? {
        val hentNesteOppgaveQuery = """
            SELECT 
                ID
            FROM 
                OPPGAVE 
            WHERE 
                ${filter.whereClause()} RESERVERT_AV IS NULL AND STATUS != 'AVSLUTTET'
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
                BEHANDLING_OPPRETTET,
                AVKLARINGSBEHOV_KODE,
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

        return connection.queryList<Oppgave>(query) {
            setParams {
                setString(1, ident)
            }
            setRowMapper { row ->
                Oppgave(
                    id = OppgaveId(row.getLong("ID")),
                    saksnummer = Saksnummer(row.getString("SAKSNUMMER")),
                    behandlingRef = BehandlingRef(row.getUUID("BEHANDLING_REF")),
                    behandlingOpprettet = row.getLocalDateTime("BEHANDLING_OPPRETTET"),
                    avklaringsbehovKode = AvklaringsbehovKode(row.getString("AVKLARINGSBEHOV_KODE")),
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
        if (avklaringsbehovKoder.isNotEmpty()) {
            return "AVKLARINGSBEHOV_KODE IN ${avklaringsbehovKoder.tilStringListe()} AND "
        }
        return ""
    }

    private fun Set<AvklaringsbehovKode>.tilStringListe(): String {
        return map {"'${it.kode}'"}.joinToString(prefix = "(", postfix = ")", separator = ", ")
    }
}