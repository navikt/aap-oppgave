package no.nav.aap.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.collections.map

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
                BEHANDLINGSTYPE,
                OPPRETTET_AV,
                OPPRETTET_TIDSPUNKT,
                PERSON_IDENT
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            
        """.trimIndent()
        val id = connection.executeReturnKey(query) {
            setParams {
                setString(1, oppgaveDto.saksnummer)
                setUUID(2, oppgaveDto.behandlingRef)
                setLong(3, oppgaveDto.journalpostId)
                setLocalDateTime(4, oppgaveDto.behandlingOpprettet)
                setString(5, oppgaveDto.avklaringsbehovKode)
                setString(6, oppgaveDto.status.name)
                setString(7, oppgaveDto.behandlingstype.name)
                setString(8, oppgaveDto.opprettetAv)
                setLocalDateTime(9, oppgaveDto.opprettetTidspunkt)
                setString(10, oppgaveDto.personIdent)
            }
        }
        return OppgaveId(id, 0L)
    }

    fun hentOppgave(avklaringsbehovReferanse: AvklaringsbehovReferanseDto): OppgaveDto? {
        val saksnummerClause = if (avklaringsbehovReferanse.saksnummer != null) "SAKSNUMMER = ?" else "SAKSNUMMER IS NULL"
        val referanseClause = if (avklaringsbehovReferanse.referanse != null) "BEHANDLING_REF = ?" else "BEHANDLING_REF IS NULL"
        val journalpostIdClause = if (avklaringsbehovReferanse.journalpostId != null) "JOURNALPOST_ID = ?" else "JOURNALPOST_ID IS NULL"
        val oppgaverForReferanseQuery = """
            SELECT 
                $alleOppgaveFelt
            FROM 
                OPPGAVE 
            WHERE 
                $saksnummerClause AND
                $referanseClause AND
                $journalpostIdClause AND
                AVKLARINGSBEHOV_TYPE = ?
        """.trimIndent()

        val oppgaver =  connection.queryList<OppgaveDto>(oppgaverForReferanseQuery) {
            setParams {
                var index = 1
                if (avklaringsbehovReferanse.saksnummer != null) setString(index++, avklaringsbehovReferanse.saksnummer)
                if (avklaringsbehovReferanse.referanse != null) setUUID(index++, avklaringsbehovReferanse.referanse)
                if (avklaringsbehovReferanse.journalpostId != null ) setLong(index++, avklaringsbehovReferanse.journalpostId)
                setString(index++, avklaringsbehovReferanse.avklaringsbehovKode)
            }
            setRowMapper { row ->
                oppgaveMapper(row)
            }
        }
        if (oppgaver.size > 1) {
            log.warn("Hent oppgaver skal ikke returnere mer en 1 oppgave. Kall med $avklaringsbehovReferanse fant ${oppgaver.size} oppgaver.")
        }
        return oppgaver.firstOrNull()
    }

    fun hentOppgaver(saksnummer: String?, referanse: UUID?, journalpostId: Long?): List<OppgaveDto> {
        val saksnummerClause = if (saksnummer != null) "SAKSNUMMER = ?" else "SAKSNUMMER IS NULL"
        val referanseClause = if (referanse != null) "BEHANDLING_REF = ?" else "BEHANDLING_REF IS NULL"
        val journalpostIdClause = if (journalpostId != null) "JOURNALPOST_ID = ?" else "JOURNALPOST_ID IS NULL"
        val oppgaverForReferanseQuery = """
            SELECT 
                $alleOppgaveFelt
            FROM 
                OPPGAVE 
            WHERE 
                $saksnummerClause AND
                $referanseClause AND
                $journalpostIdClause
        """.trimIndent()

        return connection.queryList<OppgaveDto>(oppgaverForReferanseQuery) {
            setParams {
                var index = 1
                if (saksnummer != null) setString(index++, saksnummer)
                if (referanse != null) setUUID(index++, referanse)
                if (journalpostId != null ) setLong(index++, journalpostId)
            }
            setRowMapper { row ->
                oppgaveMapper(row)
            }
        }
    }

    fun gjenåpneOppgave(oppgaveId: OppgaveId, ident: String) {
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                STATUS = 'OPPRETTET', 
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND
                STATUS != 'OPPRETTET' AND
                VERSJON = ?
                
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setString(1, ident)
                setLong(2, oppgaveId.id)
                setLong(3, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) }
        }
    }

    fun avsluttOppgave(oppgaveId: OppgaveId, ident: String) {
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                STATUS = 'AVSLUTTET', 
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP,
                RESERVERT_AV = NULL,
                RESERVERT_TIDSPUNKT = NULL,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND
                STATUS != 'AVSLUTTET' AND
                VERSJON = ?
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setString(1, ident)
                setLong(2, oppgaveId.id)
                setLong(3, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) }
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
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND 
                STATUS != 'AVSLUTTET' AND
                VERSJON = ?
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setString(1, ident)
                setLong(2, oppgaveId.id)
                setLong(3, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) }
        }
    }

    fun finnNesteOppgaver(filterDto: FilterDto, limit: Int = 1): List<NesteOppgaveDto> {
        val hentNesteOppgaveQuery = """
            SELECT 
                ID, VERSJON, SAKSNUMMER, BEHANDLING_REF, JOURNALPOST_ID, AVKLARINGSBEHOV_TYPE
            FROM 
                OPPGAVE 
            WHERE 
                ${filterDto.whereClause()} RESERVERT_AV IS NULL AND STATUS != 'AVSLUTTET'
            ORDER BY BEHANDLING_OPPRETTET
            LIMIT $limit
            FOR UPDATE
            SKIP LOCKED
        """.trimIndent()

        return connection.queryList(hentNesteOppgaveQuery) {
            setRowMapper {
                NesteOppgaveDto(
                    oppgaveId = it.getLong("ID"),
                    oppgaveVersjon = it.getLong("VERSJON"),
                    AvklaringsbehovReferanseDto(
                        saksnummer = it.getStringOrNull("SAKSNUMMER"),
                        referanse = it.getUUIDOrNull("BEHANDLING_REF"),
                        journalpostId = it.getLongOrNull("JOURNALPOST_ID"),
                        avklaringsbehovKode = it.getString("AVKLARINGSBEHOV_TYPE")
                    )
                )
            }
        }
    }

    fun reserverOppgave(oppgaveId: OppgaveId, ident: String, reservertAvIdent: String) {
        val updaterOppgaveReservasjonQuery = """
            UPDATE 
                OPPGAVE 
            SET 
                RESERVERT_AV = ?,
                RESERVERT_TIDSPUNKT = CURRENT_TIMESTAMP,
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND
                VERSJON = ?
        """.trimIndent()

        connection.execute(updaterOppgaveReservasjonQuery) {
            setParams {
                setString(1, reservertAvIdent)
                setString(2, ident)
                setLong(3, oppgaveId.id)
                setLong(4, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) }
        }
    }

    fun hentMineOppgaver(ident: String): List<OppgaveDto> {
        val query = """
            SELECT 
                $alleOppgaveFelt
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
            setRowMapper { row -> oppgaveMapper(row) }
        }
    }

    fun hentAlleÅpneOppgaver(): List<OppgaveDto> {
        val query = """
            SELECT 
                $alleOppgaveFelt
            FROM
                OPPGAVE    
            WHERE
                STATUS != 'AVSLUTTET'
        """.trimIndent()

        return connection.queryList<OppgaveDto>(query) {
            setRowMapper { row -> oppgaveMapper(row) }
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
                ID, VERSJON
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
                setString(index++, avklaringsbehovReferanse.avklaringsbehovKode)
            }
            setRowMapper { row ->
                OppgaveId(row.getLong("ID"), row.getLong("VERSJON"))
            }
        }
        if (oppgaver.size > 1) {
            log.warn("Hent oppgaver skal ikke returnere mer en 1 oppgave. Kall med $avklaringsbehovReferanse fant ${oppgaver.size} oppgaver.")
        }
        return oppgaver
    }

    private fun FilterDto.whereClause(): String {
        val sb = StringBuilder()
        if (avklaringsbehovKoder.isNotEmpty()) {
            val stringListeAvklaringsbehovKoder = avklaringsbehovKoder
                .map {"'$it'"}
                .joinToString(prefix = "(", postfix = ")", separator = ", ")
            sb.append("AVKLARINGSBEHOV_TYPE IN $stringListeAvklaringsbehovKoder AND ")
        }
        if (behandlingstyper.isNotEmpty()) {
            val stringListeAvBehandlingstyper = behandlingstyper
                .map { "'${it.name}'" }
                .joinToString(prefix = "(", postfix = ")", separator = ", ")
            sb.append("BEHANDLINGSTYPE in $stringListeAvBehandlingstyper AND ")
        }
        return sb.toString()
    }

    private fun oppgaveMapper(row: Row): OppgaveDto {
        return OppgaveDto(
            id = row.getLong("ID"),
            personIdent = row.getStringOrNull("PERSON_IDENT"),
            saksnummer = row.getStringOrNull("SAKSNUMMER"),
            behandlingRef = row.getUUIDOrNull("BEHANDLING_REF"),
            journalpostId = row.getLongOrNull("JOURNALPOST_ID"),
            behandlingOpprettet = row.getLocalDateTime("BEHANDLING_OPPRETTET"),
            avklaringsbehovKode = row.getString("AVKLARINGSBEHOV_TYPE"),
            status = Status.valueOf(row.getString("STATUS")),
            behandlingstype = Behandlingstype.valueOf(row.getString("BEHANDLINGSTYPE")),
            reservertAv = row.getStringOrNull("RESERVERT_AV"),
            reservertTidspunkt = row.getLocalDateTimeOrNull("RESERVERT_TIDSPUNKT"),
            opprettetAv = row.getString("OPPRETTET_AV"),
            opprettetTidspunkt = row.getLocalDateTime("OPPRETTET_TIDSPUNKT"),
            endretAv = row.getStringOrNull("ENDRET_AV"),
            endretTidspunkt = row.getLocalDateTimeOrNull("ENDRET_TIDSPUNKT"),
            versjon = row.getLong("VERSJON"),
        )
    }

    private companion object {
        val alleOppgaveFelt = """
            ID,
            PERSON_IDENT,
            SAKSNUMMER,
            BEHANDLING_REF,
            JOURNALPOST_ID,
            BEHANDLING_OPPRETTET,
            AVKLARINGSBEHOV_TYPE,
            STATUS,
            BEHANDLINGSTYPE,
            RESERVERT_AV,
            RESERVERT_TIDSPUNKT,
            OPPRETTET_AV,
            OPPRETTET_TIDSPUNKT,
            ENDRET_AV,
            ENDRET_TIDSPUNKT,
            VERSJON            
        """.trimIndent()
    }

}