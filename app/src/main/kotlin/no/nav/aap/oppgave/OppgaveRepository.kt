package no.nav.aap.oppgave

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

private val log = LoggerFactory.getLogger(OppgaveRepository::class.java)

class OppgaveRepository(private val connection: DBConnection) {

    fun opprettOppgave(oppgaveDto: OppgaveDto): OppgaveId {
        val query = """
            INSERT INTO OPPGAVE (
                SAKSNUMMER,
                BEHANDLING_REF,
                JOURNALPOST_ID,
                ENHET,
                OPPFOLGINGSENHET,
                BEHANDLING_OPPRETTET,
                AVKLARINGSBEHOV_TYPE,
                STATUS,
                BEHANDLINGSTYPE,
                PAA_VENT_TIL,
                PAA_VENT_AARSAK,
                OPPRETTET_AV,
                OPPRETTET_TIDSPUNKT,
                PERSON_IDENT,
                VEILEDER
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            
        """.trimIndent()
        val id = connection.executeReturnKey(query) {
            setParams {
                setString(1, oppgaveDto.saksnummer)
                setUUID(2, oppgaveDto.behandlingRef)
                setLong(3, oppgaveDto.journalpostId)
                setString(4, oppgaveDto.enhet)
                setString(5, oppgaveDto.oppfølgingsenhet)
                setLocalDateTime(6, oppgaveDto.behandlingOpprettet)
                setString(7, oppgaveDto.avklaringsbehovKode)
                setString(8, oppgaveDto.status.name)
                setString(9, oppgaveDto.behandlingstype.name)
                setLocalDate(10, oppgaveDto.påVentTil)
                setString(11, oppgaveDto.påVentÅrsak)
                setString(12, oppgaveDto.opprettetAv)
                setLocalDateTime(13, oppgaveDto.opprettetTidspunkt)
                setString(14, oppgaveDto.personIdent)
                setString(15, oppgaveDto.veileder)
            }
        }
        return OppgaveId(id, 0L)
    }

    fun hentOppgave(avklaringsbehovReferanse: AvklaringsbehovReferanseDto): OppgaveDto? {
        val saksnummerClause =
            if (avklaringsbehovReferanse.saksnummer != null) "SAKSNUMMER = ?" else "SAKSNUMMER IS NULL"
        val referanseClause =
            if (avklaringsbehovReferanse.referanse != null) "BEHANDLING_REF = ?" else "BEHANDLING_REF IS NULL"
        val journalpostIdClause =
            if (avklaringsbehovReferanse.journalpostId != null) "JOURNALPOST_ID = ?" else "JOURNALPOST_ID IS NULL"
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

        val oppgaver = connection.queryList(oppgaverForReferanseQuery) {
            setParams {
                var index = 1
                if (avklaringsbehovReferanse.saksnummer != null) setString(index++, avklaringsbehovReferanse.saksnummer)
                if (avklaringsbehovReferanse.referanse != null) setUUID(index++, avklaringsbehovReferanse.referanse)
                if (avklaringsbehovReferanse.journalpostId != null) setLong(
                    index++,
                    avklaringsbehovReferanse.journalpostId
                )
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

    fun hentOppgave(oppgaveId: OppgaveId): OppgaveDto {
        val oppgaverForIdQuery = """
            SELECT 
                $alleOppgaveFelt
            FROM 
                OPPGAVE 
            WHERE 
                ID = ?
        """.trimIndent()

        return connection.queryFirst(oppgaverForIdQuery) {
            setParams {
                setLong(1, oppgaveId.id)
            }
            setRowMapper { row ->
                oppgaveMapper(row)
            }
        }
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

        return connection.queryList(oppgaverForReferanseQuery) {
            setParams {
                var index = 1
                if (saksnummer != null) setString(index++, saksnummer)
                if (referanse != null) setUUID(index++, referanse)
                if (journalpostId != null) setLong(index++, journalpostId)
            }
            setRowMapper { row ->
                oppgaveMapper(row)
            }
        }
    }

    fun gjenåpneOppgave(
        oppgaveId: OppgaveId,
        ident: String
    ) {
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
                STATUS = 'AVSLUTTET' AND
                VERSJON = ?
        """.trimIndent()

        connection.execute(query) {
            setParams {
                setString(1, ident)
                setLong(2, oppgaveId.id)
                setLong(3, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) { "Forventer bare at én oppgave gjenåpnes. Fant ${it} oppgaver. Oppgave-Id: ${oppgaveId.id}" } }
        }
    }

    fun oppdatereOppgave(
        oppgaveId: OppgaveId,
        ident: String,
        personIdent: String?,
        enhet: String,
        påVentTil: LocalDate?,
        påVentÅrsak: String?,
        oppfølgingsenhet: String?,
        veileder: String?
    ) {
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                STATUS = 'OPPRETTET', 
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP,
                ENHET = ?,
                OPPFOLGINGSENHET = ?,
                PAA_VENT_TIL = ?,
                PAA_VENT_AARSAK= ?,
                PERSON_IDENT = ?,
                VEILEDER = ?,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND
                VERSJON = ?
                
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setString(1, ident)
                setString(2, enhet)
                setString(3, oppfølgingsenhet)
                setLocalDate(4, påVentTil)
                setString(5, påVentÅrsak)
                setString(6, personIdent)
                setString(7, veileder)
                setLong(8, oppgaveId.id)
                setLong(9, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) { "Prøvde å oppdatere én oppgave, men fant $it oppgaver. Oppgave-Id: ${oppgaveId.id}" } }
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

    enum class Rekkefølge { asc, desc }

    fun finnOppgaver(filter: Filter, rekkefølge: Rekkefølge = Rekkefølge.asc): List<OppgaveDto> {
        val hentNesteOppgaveQuery = """
            SELECT 
                   $alleOppgaveFelt
            FROM 
                OPPGAVE 
            WHERE 
                ${filter.whereClause()} RESERVERT_AV IS NULL AND STATUS != 'AVSLUTTET'
            ORDER BY BEHANDLING_OPPRETTET ${rekkefølge.name}
        """.trimIndent()

        return connection.queryList(hentNesteOppgaveQuery) {
            setRowMapper {
                oppgaveMapper(it)
            }
        }
    }

    data class IdentMedOppgaveId(val ident: String, val oppgaveId: Long, val versjon: Long)

    fun finnÅpneOppgaverIkkeVikafossen(): List<IdentMedOppgaveId> {
        val query = """
            SELECT 
                PERSON_IDENT, ID, VERSJON
            FROM 
                OPPGAVE 
            WHERE 
                ENHET != '2103'
                AND STATUS != 'AVSLUTTET'
        """.trimIndent()

        return connection.queryList(query) {
            setRowMapper { row ->
                IdentMedOppgaveId(row.getString("PERSON_IDENT"), row.getLong("ID"), row.getLong("VERSJON"))
            }
        }
    }

    fun oppdaterOppgaveEnhetOgFjernReservasjonBatch(oppgaveIds: List<Long>, enhet: String): Int {
        require(oppgaveIds.isNotEmpty()) { "Må ha minst en oppgave å oppdatere" }
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                ENHET = ?,
                RESERVERT_AV = NULL,
                ENDRET_AV = ?,
                ENDRET_TIDSPUNKT = CURRENT_TIMESTAMP,
                VERSJON = VERSJON + 1
            WHERE 
                ID IN (${oppgaveIds.joinToString(",")})
        """.trimIndent()

        return connection.executeReturnUpdated(query) {
            setParams {
                setString(1, enhet)
                setString(
                    2,
                    "Kelvin"
                ) // TODO: Kan øke kolonnestørrelse for å få plass til jobbtype hvis det er interessant
            }
        }
    }

    fun finnOppgaverGittSaksnummer(saksnummer: String): List<OppgaveDto> {
        val hentOppgaverGittSaksnummerQuery = """
            SELECT 
                   $alleOppgaveFelt
            FROM 
                OPPGAVE 
            WHERE 
                SAKSNUMMER = ? AND STATUS != 'AVSLUTTET'
            ORDER BY BEHANDLING_OPPRETTET
        """.trimIndent()

        return connection.queryList(hentOppgaverGittSaksnummerQuery) {
            setParams {
                setString(1, saksnummer)
            }
            setRowMapper {
                oppgaveMapper(it)
            }
        }
    }

    fun finnOppgaverGittPersonident(personIdent: String): List<OppgaveDto> {
        val hentOppgaverGittPersonidentQuery = """
            SELECT 
                   $alleOppgaveFelt
            FROM 
                OPPGAVE 
            WHERE 
                PERSON_IDENT = ? AND STATUS != 'AVSLUTTET'
            ORDER BY BEHANDLING_OPPRETTET
        """.trimIndent()

        return connection.queryList(hentOppgaverGittPersonidentQuery) {
            setParams {
                setString(1, personIdent)
            }
            setRowMapper {
                oppgaveMapper(it)
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
        val saksnummerClause =
            if (avklaringsbehovReferanse.saksnummer != null) "SAKSNUMMER = ?" else "SAKSNUMMER IS NULL"
        val referanseClause =
            if (avklaringsbehovReferanse.referanse != null) "BEHANDLING_REF = ?" else "BEHANDLING_REF IS NULL"
        val journalpostIdClause =
            if (avklaringsbehovReferanse.journalpostId != null) "JOURNALPOST_ID = ?" else "JOURNALPOST_ID IS NULL"
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

        val oppgaver = connection.queryList<OppgaveId>(oppgaverForReferanseQuery) {
            setParams {
                var index = 1
                if (avklaringsbehovReferanse.saksnummer != null) setString(index++, avklaringsbehovReferanse.saksnummer)
                if (avklaringsbehovReferanse.referanse != null) setUUID(index++, avklaringsbehovReferanse.referanse)
                if (avklaringsbehovReferanse.journalpostId != null) setLong(
                    index++,
                    avklaringsbehovReferanse.journalpostId
                )
                setString(index++, avklaringsbehovReferanse.avklaringsbehovKode)
            }
            setRowMapper { row ->
                OppgaveId(row.getLong("ID"), row.getLong("VERSJON"))
            }
        }
        if (oppgaver.size > 1) {
            log.warn("Hent oppgave skal ikke returnere mer enn 1 oppgave. Kall med $avklaringsbehovReferanse fant ${oppgaver.size} oppgaver.")
        }
        return oppgaver
    }

    private fun Filter.whereClause(): String {
        val sb = StringBuilder()

        // Avklaringsbehov
        if (avklaringsbehovKoder.isNotEmpty()) {
            val stringListeAvklaringsbehovKoder =
                avklaringsbehovKoder.joinToString(prefix = "(", postfix = ")", separator = ", ") { "'$it'" }
            sb.append("AVKLARINGSBEHOV_TYPE IN $stringListeAvklaringsbehovKoder AND ")
        }
        // Behandlingstyper
        if (behandlingstyper.isNotEmpty()) {
            val stringListeAvBehandlingstyper =
                behandlingstyper.joinToString(prefix = "(", postfix = ")", separator = ", ") { "'${it.name}'" }
            sb.append("BEHANDLINGSTYPE in $stringListeAvBehandlingstyper AND ")
        }
        // Enheter
        if (enheter.isNotEmpty()) {
            val stringListeEnheter = enheter.joinToString(prefix = "(", postfix = ")", separator = ", ") { "'$it'" }
            sb.append("(OPPFOLGINGSENHET IN $stringListeEnheter OR (OPPFOLGINGSENHET IS NULL AND ENHET IN $stringListeEnheter)) AND ")
        }
        // Veileder
        if (veileder != null) {
            sb.append("VEILEDER = '$veileder' AND ")
        }
        // På vent
        sb.append("PAA_VENT_TIL IS NULL AND ")

        return sb.toString()
    }

    private fun oppgaveMapper(row: Row): OppgaveDto {
        return OppgaveDto(
            id = row.getLong("ID"),
            personIdent = row.getStringOrNull("PERSON_IDENT"),
            saksnummer = row.getStringOrNull("SAKSNUMMER"),
            behandlingRef = row.getUUIDOrNull("BEHANDLING_REF"),
            journalpostId = row.getLongOrNull("JOURNALPOST_ID"),
            enhet = row.getString("ENHET"),
            oppfølgingsenhet = row.getStringOrNull("OPPFOLGINGSENHET"),
            veileder = row.getStringOrNull("VEILEDER"),
            behandlingOpprettet = row.getLocalDateTime("BEHANDLING_OPPRETTET"),
            avklaringsbehovKode = row.getString("AVKLARINGSBEHOV_TYPE"),
            status = Status.valueOf(row.getString("STATUS")),
            behandlingstype = Behandlingstype.valueOf(row.getString("BEHANDLINGSTYPE")),
            påVentTil = row.getLocalDateOrNull("PAA_VENT_TIL"),
            påVentÅrsak = row.getStringOrNull("PAA_VENT_AARSAK"),
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
            ENHET,
            OPPFOLGINGSENHET,
            VEILEDER,
            BEHANDLING_OPPRETTET,
            AVKLARINGSBEHOV_TYPE,
            STATUS,
            BEHANDLINGSTYPE,
            PAA_VENT_TIL,
            PAA_VENT_AARSAK,
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