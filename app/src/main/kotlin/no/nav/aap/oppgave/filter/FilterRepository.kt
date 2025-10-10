package no.nav.aap.oppgave.filter

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.time.LocalDateTime

data class OpprettFilter(
    val navn: String,
    val beskrivelse: String,
    val avklaringsbehovtyper: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val enhetFilter: List<EnhetFilter>? = null,
)

data class EnhetFilter(
    val enhetNr: String,
    val filtermodus: Filtermodus
)

data class OppdaterFilter(
    val id: Long,
    val navn: String,
    val beskrivelse: String,
    val avklaringsbehovtyper: Set<String> = emptySet(),
    val behandlingstyper: Set<Behandlingstype> = emptySet(),
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
)

class FilterRepository(private val connection: DBConnection) {

    fun hentAlle(): List<FilterDto> {
        return hentFilter(null)
    }

    fun hent(filterId: Long): FilterDto? {
        return hentFilter(filterId).firstOrNull()
    }

    fun hentForEnheter(enheter: List<String>?): List<FilterDto> {
        return hentFilterForEnheter(enheter)
    }

    fun opprett(filter: OpprettFilter): Long {
        val filterId = opprettFilter(filter)
        opprettFilterAvklaringsbehovtyper(filterId, filter.avklaringsbehovtyper)
        opprettFilterBehandlingstyper(filterId, filter.behandlingstyper)
        opprettFilterEnheter(filterId, filter.enhetFilter)
        return filterId
    }

    fun oppdater(filter: OppdaterFilter): Long {
        oppdaterFilter(filter)
        slettFilterParametre(filter.id)
        opprettFilterAvklaringsbehovtyper(filter.id, filter.avklaringsbehovtyper)
        opprettFilterBehandlingstyper(filter.id, filter.behandlingstyper)
        return filter.id
    }

    fun slettFilter(id: Long) {
        connection.execute("UPDATE FILTER SET SLETTET = TRUE WHERE ID = ?") {
            setParams {
                setLong(1, id)
            }
        }
    }

    private fun oppdaterFilter(filter: OppdaterFilter) {
        connection.execute("UPDATE FILTER SET NAVN = ?, BESKRIVELSE = ?, ENDRET_AV = ?, ENDRET_TIDSPUNKT = ? WHERE ID = ?") {
            setParams {
                setString(1, filter.navn)
                setString(2, filter.beskrivelse)
                setString(3, filter.endretAv)
                setLocalDateTime(4, filter.endretTidspunkt)
                setLong(5, filter.id)
            }
        }
    }

    private fun slettFilterParametre(filterId: Long) {
        connection.execute("DELETE FROM FILTER_AVKLARINGSBEHOVTYPE WHERE FILTER_ID = ?") {
            setParams { setLong(1, filterId) }
        }
        connection.execute("DELETE FROM FILTER_BEHANDLINGSTYPE WHERE FILTER_ID = ?") {
            setParams { setLong(1, filterId) }
        }
    }

    private fun opprettFilter(filter: OpprettFilter): Long {
        val insertFilterSql = """
            INSERT INTO FILTER (NAVN, BESKRIVELSE, OPPRETTET_AV, OPPRETTET_TIDSPUNKT) VALUES (?, ?, ?, ?)
        """.trimIndent()

        return connection.executeReturnKey(insertFilterSql) {
            setParams {
                setString(1, filter.navn)
                setString(2, filter.beskrivelse)
                setString(3, filter.opprettetAv)
                setLocalDateTime(4, filter.opprettetTidspunkt)
            }
        }
    }

    private fun opprettFilterAvklaringsbehovtyper(filterId: Long, avklaringsbehovtyper: Set<String>) {
        val insertFilterAvklaringsbehovtypeSql = """
            INSERT INTO FILTER_AVKLARINGSBEHOVTYPE (FILTER_ID, AVKLARINGSBEHOVTYPE) VALUES (?, ?)
        """.trimIndent()

        connection.executeBatch(insertFilterAvklaringsbehovtypeSql, avklaringsbehovtyper) {
            setParams {
                setLong(1, filterId)
                setString(2, it)
            }
        }
    }

    private fun opprettFilterBehandlingstyper(filterId: Long, behandlingstyper: Set<Behandlingstype>) {
        val insertFilterAvklaringsbehovtypeSql = """
            INSERT INTO FILTER_BEHANDLINGSTYPE (FILTER_ID, BEHANDLINGSTYPE) VALUES (?, ?)
        """.trimIndent()

        connection.executeBatch(insertFilterAvklaringsbehovtypeSql, behandlingstyper) {
            setParams {
                setLong(1, filterId)
                setString(2, it.name)
            }
        }
    }

    fun opprettFilterEnheter(filterId: Long, enhetFilter: List<EnhetFilter>? = null) {
        val insertFilterEnheterSql = """
            INSERT INTO FILTER_ENHET (FILTER_ID, ENHET, FILTER_MODUS) VALUES (?, ?, ?)
        """.trimIndent()

        if (enhetFilter.isNullOrEmpty()) {
            connection.execute(insertFilterEnheterSql) {
                setParams {
                    setLong(1, filterId)
                    setString(2, "ALLE")
                    setString(3, Filtermodus.INKLUDER.name)
                }
            }
            return
        }

        connection.executeBatch(insertFilterEnheterSql, enhetFilter) {
            setParams {
                setLong(1, filterId)
                setString(2, it.enhetNr)
                setString(3, it.filtermodus.name)
            }
        }
    }

    private fun hentFilterForEnheter(enheter: List<String>?): List<FilterDto> {
        val enhetsfilter = if (!enheter.isNullOrEmpty())
            """AND ID IN (SELECT FILTER_ID FROM FILTER_ENHET WHERE FILTER_MODUS = 'INKLUDER' AND (ENHET = 'ALLE' OR ENHET = ANY(?::text[])))
               AND ID NOT IN (SELECT FILTER_ID FROM FILTER_ENHET WHERE FILTER_MODUS = 'EKSKLUDER' AND (ENHET = 'ALLE' OR ENHET = ANY(?::text[])))""".trimIndent()
        else ""

        val query = """
            SELECT 
                ID, NAVN, BESKRIVELSE, OPPRETTET_AV, OPPRETTET_TIDSPUNKT, ENDRET_AV, ENDRET_TIDSPUNKT
            FROM 
                FILTER 
            WHERE SLETTET = FALSE 
                $enhetsfilter
            GROUP BY FILTER.ID
        """.trimIndent()

        val alleFilter = connection.queryList(query) {
            setParams {
                if (!enheter.isNullOrEmpty()) {
                    setArray(1, enheter)
                    setArray(2, enheter)
                }
            }
            setRowMapper { row ->
                FilterDto(
                    id = row.getLong("ID"),
                    navn = row.getString("NAVN"),
                    beskrivelse = row.getString("BESKRIVELSE"),
                    opprettetAv = row.getString("OPPRETTET_AV"),
                    opprettetTidspunkt = row.getLocalDateTime("OPPRETTET_TIDSPUNKT"),
                    endretAv = row.getStringOrNull("ENDRET_AV"),
                    endretTidspunkt = row.getLocalDateTimeOrNull("ENDRET_TIDSPUNKT"),
                )
            }
        }

        val alleFilterAvklaringsbehovtype = hentAlleFilterAvklaringsbehovtype(null)
        val alleFilterBehandlingstyper = hentAlleFilterBehandlingstyper(null)
        val alleFilterMedFelter = alleFilter.map { filter ->
            filter.copy(
                avklaringsbehovKoder = alleFilterAvklaringsbehovtype[filter.id] ?: emptySet(),
                behandlingstyper = alleFilterBehandlingstyper[filter.id] ?: emptySet()
            )
        }
        return alleFilterMedFelter
    }


    private fun hentFilter(filterId: Long?): List<FilterDto> {
        val filterIdClause = if (filterId != null) " AND ID = ?" else ""
        val query = """
            SELECT 
                ID, NAVN, BESKRIVELSE, OPPRETTET_AV, OPPRETTET_TIDSPUNKT, ENDRET_AV, ENDRET_TIDSPUNKT
            FROM 
                FILTER 
            WHERE 
                SLETTET = FALSE $filterIdClause
        """.trimIndent()

        val alleFilter = connection.queryList(query) {
            setParams {
                if (filterId != null) {
                    setLong(1, filterId)
                }
            }
            setRowMapper { row ->
                FilterDto(
                    id = row.getLong("ID"),
                    navn = row.getString("NAVN"),
                    beskrivelse = row.getString("BESKRIVELSE"),
                    opprettetAv = row.getString("OPPRETTET_AV"),
                    opprettetTidspunkt = row.getLocalDateTime("OPPRETTET_TIDSPUNKT"),
                    endretAv = row.getStringOrNull("ENDRET_AV"),
                    endretTidspunkt = row.getLocalDateTimeOrNull("ENDRET_TIDSPUNKT"),
                )
            }
        }
        val alleFilterAvklaringsbehovtype = hentAlleFilterAvklaringsbehovtype(filterId)
        val alleFilterBehandlingstyper = hentAlleFilterBehandlingstyper(filterId)
        val alleFilterMedFelter = alleFilter.map { filter ->
            filter.copy(
                avklaringsbehovKoder = alleFilterAvklaringsbehovtype[filter.id] ?: emptySet(),
                behandlingstyper = alleFilterBehandlingstyper[filter.id] ?: emptySet()
            )
        }
        return alleFilterMedFelter
    }

    private fun hentAlleFilterAvklaringsbehovtype(filterId: Long?): Map<Long, Set<String>> {
        val filterIdClause = if (filterId != null) " AND FILTER_ID = ?" else ""
        val query = """
            SELECT
                F.ID AS FILTER_ID,
                FA.AVKLARINGSBEHOVTYPE
            FROM
                FILTER F,
                FILTER_AVKLARINGSBEHOVTYPE FA
            WHERE
                F.ID = FA.FILTER_ID AND
                F.SLETTET = FALSE
                $filterIdClause
        """.trimIndent()

        val alleAvklaringsbehovFiltre = connection.queryList(query) {
            setParams {
                if (filterId != null) {
                    setLong(1, filterId)
                }
            }
            setRowMapper { row ->
                Pair(row.getLong("FILTER_ID"), row.getString("AVKLARINGSBEHOVTYPE"))
            }
        }

        return alleAvklaringsbehovFiltre
            .groupBy { it.first }
            .mapValues { entry ->
                entry.value.map {
                    it.second
                }.toSet()
            }
    }

    private fun hentAlleFilterBehandlingstyper(filterId: Long?): Map<Long, Set<Behandlingstype>> {
        val filterIdClause = if (filterId != null) " AND FILTER_ID = ?" else ""
        val query = """
            SELECT
                F.ID AS FILTER_ID,
                FB.BEHANDLINGSTYPE
            FROM
                FILTER F,
                FILTER_BEHANDLINGSTYPE FB
            WHERE
                F.ID = FB.FILTER_ID AND
                F.SLETTET = FALSE
                $filterIdClause
        """.trimIndent()

        val alleBehandlingstypeFiltre = connection.queryList(query) {
            setParams {
                if (filterId != null) {
                    setLong(1, filterId)
                }
            }
            setRowMapper { row ->
                Pair(
                    row.getLong("FILTER_ID"),
                    Behandlingstype.valueOf(row.getString("BEHANDLINGSTYPE"))
                )
            }
        }

        return alleBehandlingstypeFiltre
            .groupBy { it.first }
            .mapValues { entry ->
                entry.value.map {
                    it.second
                }.toSet()
            }
    }

}

enum class Filtermodus {
    INKLUDER,
    EKSKLUDER
}

