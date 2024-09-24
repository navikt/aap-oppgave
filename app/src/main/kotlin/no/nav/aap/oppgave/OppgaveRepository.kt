package no.nav.aap.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.opprett.Avklaringsbehovtype
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import no.nav.aap.oppgave.verdityper.OppgaveId
import no.nav.aap.oppgave.verdityper.Status
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(OppgaveRepository::class.java)

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
                setString(5, oppgaveDto.avklaringsbehovKode.kode)
                setString(6, oppgaveDto.status.name)
                setString(7, oppgaveDto.opprettetAv)
                setLocalDateTime(8, oppgaveDto.opprettetTidspunkt)
            }
        }
        return OppgaveId(id)
    }

    fun avsluttOppgave(oppgaveId: OppgaveId, ident: String) {
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                STATUS = 'AVSLUTTET', 
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP
            WHERE 
                ID = ? AND
                STATUS != 'AVSLUTTET'
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setString(1, ident)
                setLong(2, oppgaveId.id)
            }
        }
    }

    fun avreserverOppgave(oppgaveId: OppgaveId, ident: String) {
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                RESERVERT_AV = NULL, 
                RESERVERT_TIDSPUNKT = NULL,
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP
            WHERE 
                ID = ? AND 
                STATUS != 'AVSLUTTET'
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setString(1, ident)
                setLong(2, oppgaveId.id)
            }
        }
    }

    fun finnNesteOppgave(filterDto: FilterDto): NesteOppgaveDto? {
        val hentNesteOppgaveQuery = """
            SELECT 
                ID, SAKSNUMMER, BEHANDLING_REF, JOURNALPOST_ID, AVKLARINGSBEHOV_TYPE
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
                    AvklaringsbehovReferanseDto(
                        saksnummer = it.getStringOrNull("SAKSNUMMER"),
                        referanse = it.getUUIDOrNull("BEHANDLING_REF"),
                        journalpostId = it.getLongOrNull("JOURNALPOST_ID"),
                        avklaringsbehovtype = Avklaringsbehovtype.fraKode(it.getString("AVKLARINGSBEHOV_TYPE"))
                    )
                )
            }
        }
        return nesteOppgave
    }

    fun reserverOppgave(oppgaveId: OppgaveId, ident: String, reservertAvIdent: String) {
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
                setString(1, reservertAvIdent)
                setString(2, ident)
                setLong(3, oppgaveId.id)
            }
        }
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
                    avklaringsbehovKode = AvklaringsbehovKode(row.getString("AVKLARINGSBEHOV_TYPE")),
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

    /**
     * Hent oppgaver som ikke er avsluttet.
     */
    fun hentÅpneOppgaver(avklaringsbehovReferanse: AvklaringsbehovReferanseDto): List<OppgaveId> {
        val saksnummerClause = if (avklaringsbehovReferanse.saksnummer != null) "SAKSNUMMER = ?" else "SAKSNUMMER IS NULL"
        val referanseClause = if (avklaringsbehovReferanse.referanse != null) "BEHANDLING_REF = ?" else "BEHANDLING_REF IS NULL"
        val journalpostIdClause = if (avklaringsbehovReferanse.journalpostId != null) "JOURNALPOST_ID = ?" else "JOURNALPOST_ID IS NULL"
        val oppgaverForReferanseQuery = """
            SELECT 
                ID 
            FROM 
                OPPGAVE 
            WHERE 
                $saksnummerClause AND
                $referanseClause AND
                $journalpostIdClause AND
                AVKLARINGSBEHOV_TYPE = ? AND
                STATUS != 'AVSLUTTET'
        """.trimIndent()

        val oppgaver =  connection.queryList<OppgaveId>(oppgaverForReferanseQuery) {
            setParams {
                var index = 1
                if (avklaringsbehovReferanse.saksnummer != null) setString(index++, avklaringsbehovReferanse.saksnummer)
                if (avklaringsbehovReferanse.referanse != null) setUUID(index++, avklaringsbehovReferanse.referanse)
                if (avklaringsbehovReferanse.journalpostId != null ) setLong(index++, avklaringsbehovReferanse.journalpostId)
                setString(index++, avklaringsbehovReferanse.avklaringsbehovtype.kode)
            }
            setRowMapper { row ->
                OppgaveId(row.getLong("ID"))
            }
        }
        if (oppgaver.size > 1) {
            log.warn("Hent oppgaver skal ikke returnere mer en 1 oppgave. Kall med $avklaringsbehovReferanse fant ${oppgaver.size} oppgaver.")
        }
        return oppgaver
    }

    private fun FilterDto.whereClause(): String {
        if (avklaringsbehovKoder.isNotEmpty()) {
            return "AVKLARINGSBEHOV_TYPE IN ${avklaringsbehovKoder.tilStringListe()} AND "
        }
        return ""
    }

    private fun Set<AvklaringsbehovKode>.tilStringListe(): String {
        return map {"'${it.kode}'"}.joinToString(prefix = "(", postfix = ")", separator = ", ")
    }
}