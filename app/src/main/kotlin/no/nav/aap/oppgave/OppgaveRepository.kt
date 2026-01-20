package no.nav.aap.oppgave

import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.liste.UtvidetOppgavelisteFilter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteSortering
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteSorteringRekkefølge
import no.nav.aap.oppgave.plukk.NesteOppgaveDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
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
                VEILEDER_ARBEID,
                VEILEDER_SYKDOM,
                AARSAKER_TIL_BEHANDLING,
                VENTE_BEGRUNNELSE,
                FORTROLIG_ADRESSE,
                ULESTE_DOKUMENTER,
                RETUR_AARSAK,
                retur_begrunnelse,
                retur_aarsaker,
                retur_returnert_av,
                aarsak_til_opprettelse,
                er_skjermet
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
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
                setString(15, oppgaveDto.veilederArbeid)
                setString(16, oppgaveDto.veilederSykdom)
                setArray(17, oppgaveDto.vurderingsbehov)
                setString(18, oppgaveDto.venteBegrunnelse)
                setBoolean(19, oppgaveDto.harFortroligAdresse)
                setBoolean(20, oppgaveDto.harUlesteDokumenter)
                setEnumName(21, oppgaveDto.returInformasjon?.status)
                setString(22, oppgaveDto.returInformasjon?.begrunnelse)
                setArray(23, oppgaveDto.returInformasjon?.årsaker?.map { it.name } ?: emptyList())
                setString(24, oppgaveDto.returInformasjon?.endretAv)
                setString(25, oppgaveDto.årsakTilOpprettelse)
                setBoolean(26, oppgaveDto.erSkjermet)
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

    fun hentAktivOppgave(behandlingReferanse: BehandlingReferanse): OppgaveDto? {
        val oppgaverForIdQuery = """
            SELECT 
                $alleOppgaveFelt
            FROM 
                OPPGAVE 
            WHERE 
                BEHANDLING_REF = ?
            AND STATUS = 'OPPRETTET'
            ORDER BY OPPRETTET_TIDSPUNKT DESC
        """.trimIndent()

        return connection.queryFirstOrNull(oppgaverForIdQuery) {
            setParams {
                setUUID(1, behandlingReferanse.referanse)
            }
            setRowMapper { row ->
                oppgaveMapper(row)
            }
        }
    }

    fun hentOppgave(oppgaveId: Long): OppgaveDto {
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
                setLong(1, oppgaveId)
            }
            setRowMapper { row ->
                oppgaveMapper(row)
            }
        }
    }

    fun hentOppgaver(referanse: UUID): List<OppgaveDto> {
        val oppgaverForReferanseQuery = """
        SELECT 
            $alleOppgaveFelt
        FROM 
            OPPGAVE 
        WHERE 
            BEHANDLING_REF = ?
    """.trimIndent()

        return connection.queryList(oppgaverForReferanseQuery) {
            setParams {
                setUUID(1, referanse)
            }
            setRowMapper { row ->
                oppgaveMapper(row)
            }
        }
    }

    fun oppdatereOppgave(
        oppgaveId: OppgaveId,
        endretAvIdent: String,
        personIdent: String?,
        enhet: String,
        påVentTil: LocalDate?,
        påVentÅrsak: String?,
        påVentBegrunnelse: String?,
        oppfølgingsenhet: String?,
        veilederArbeid: String?,
        veilederSykdom: String?,
        vurderingsbehov: List<String>,
        harFortroligAdresse: Boolean? = false,
        erSkjermet: Boolean,
        harUlesteDokumenter: Boolean? = false,
        returInformasjon: ReturInformasjon?,
        utløptVentefrist: LocalDate? = null
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
                VENTE_BEGRUNNELSE = ?,
                PERSON_IDENT = ?,
                VEILEDER_ARBEID = ?,
                VEILEDER_SYKDOM = ?,
                AARSAKER_TIL_BEHANDLING = ?,
                FORTROLIG_ADRESSE = ?,
                ULESTE_DOKUMENTER = ?,
                RETUR_AARSAK = ?,
                retur_returnert_av = ?,
                retur_aarsaker = ?,
                RETUR_BEGRUNNELSE = ?,
                ER_SKJERMET = ?,
                UTLOEPT_VENTEFRIST = ?,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND
                VERSJON = ?
                
        """.trimIndent()
        connection.execute(query) {
            setParams {
                setString(1, endretAvIdent)
                setString(2, enhet)
                setString(3, oppfølgingsenhet)
                setLocalDate(4, påVentTil)
                setString(5, påVentÅrsak)
                setString(6, påVentBegrunnelse)
                setString(7, personIdent)
                setString(8, veilederArbeid)
                setString(9, veilederSykdom)
                setArray(10, vurderingsbehov)
                setBoolean(11, harFortroligAdresse)
                setBoolean(12, harUlesteDokumenter)
                setEnumName(13, returInformasjon?.status)
                setString(14, returInformasjon?.endretAv)
                setArray(15, returInformasjon?.årsaker?.map { it.name } ?: emptyList())
                setString(16, returInformasjon?.begrunnelse)
                setBoolean(17, erSkjermet)
                setLocalDate(18, utløptVentefrist)
                setLong(19, oppgaveId.id)
                setLong(20, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) { "Prøvde å oppdatere én oppgave, men fant $it oppgaver. Oppgave: $oppgaveId" } }
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
                RESERVERT_AV_NAVN = NULL,
                UTLOEPT_VENTEFRIST = NULL,
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
                RESERVERT_AV_NAVN = NULL,
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
            setResultValidator { require(it == 1) { "Prøvde å avreservere én oppgave, men fant $it oppgaver. Oppgave: $oppgaveId" } }
        }
    }

    fun finnNesteOppgaver(filterDto: FilterDto, limit: Int = 1): List<NesteOppgaveDto> {
        val hentNesteOppgaveQuery = """
            SELECT 
                ID, VERSJON, SAKSNUMMER, BEHANDLING_REF, JOURNALPOST_ID, AVKLARINGSBEHOV_TYPE
            FROM 
                OPPGAVE 
            WHERE 
                ${filterDto.whereClause()} RESERVERT_AV IS NULL AND STATUS != 'AVSLUTTET' AND PAA_VENT_TIL IS NULL
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

    fun settFortroligAdresse(oppgaveId: OppgaveId, harFortroligAdresse: Boolean) {
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                FORTROLIG_ADRESSE = ?,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND
                STATUS != 'AVSLUTTET' AND
                VERSJON = ?
        """.trimIndent()

        connection.execute(query) {
            setParams {
                setBoolean(1, harFortroligAdresse)
                setLong(2, oppgaveId.id)
                setLong(3, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) { "Prøvde å oppdatere fortrolig adresse for én oppgave, men fant $it oppgaver. Oppgave: $oppgaveId" } }
        }
    }

    fun settUlesteDokumenter(oppgaveId: OppgaveId, harUlesteDokumenter: Boolean) {
        val query = """
            UPDATE 
                OPPGAVE 
            SET 
                ULESTE_DOKUMENTER = ?,
                VERSJON = VERSJON + 1
            WHERE 
                ID = ? AND
                STATUS != 'AVSLUTTET' AND
                VERSJON = ?
        """.trimIndent()

        connection.execute(query) {
            setParams {
                setBoolean(1, harUlesteDokumenter)
                setLong(2, oppgaveId.id)
                setLong(3, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) { "Prøvde å oppdatere uleste dokumenter-feltet for én oppgave, men fant $it oppgaver. Oppgave: $oppgaveId" } }
        }
    }

    enum class Rekkefølge { asc, desc }

    private fun utvidetFilterQuery(utvidetFilter: UtvidetOppgavelisteFilter): String {
        val sb = StringBuilder()

        if (utvidetFilter.årsaker.isNotEmpty()) {
            val stringListeÅrsaker =
                utvidetFilter.årsaker.joinToString(prefix = "('{", postfix = "}')", separator = ", ") { it }
            sb.append(" AND AARSAKER_TIL_BEHANDLING && $stringListeÅrsaker")
        }

        val returStatuserNotEmpty = utvidetFilter.returStatuser.isNotEmpty()
        val påVent = utvidetFilter.påVent == true

        if (påVent) {
            if (returStatuserNotEmpty) {
                val stringListeReturStatuser = utvidetFilter.returStatuser
                    .joinToString(prefix = "(", postfix = ")", separator = ", ") { "'$it'" }
                sb.append(" AND (RETUR_AARSAK IN $stringListeReturStatuser OR PAA_VENT_AARSAK IS NOT NULL)")
            } else {
                sb.append(" AND PAA_VENT_AARSAK IS NOT NULL")
            }
        } else if (returStatuserNotEmpty) {
            val stringListeReturStatuser =
                utvidetFilter.returStatuser.joinToString(prefix = "(", postfix = ")", separator = ", ") { "'$it'" }
            sb.append(" AND RETUR_AARSAK IN $stringListeReturStatuser")
        }

        if (utvidetFilter.ventefristUtløpt == true) {
            sb.append(" AND UTLOEPT_VENTEFRIST IS NOT NULL")
        }

        if (utvidetFilter.fom != null) {
            sb.append(" AND OPPRETTET_TIDSPUNKT >= '${utvidetFilter.fom}'")
        }

        if (utvidetFilter.tom != null) {
            sb.append(" AND OPPRETTET_TIDSPUNKT <= '${utvidetFilter.tom}'")
        }

        if (utvidetFilter.markertHaster == true) {
            sb.append(" AND m.MARKERING_TYPE = '${MarkeringForBehandling.HASTER}'")
        }

        return sb.toString()
    }

    fun finnOppgaver(
        filter: Filter,
        rekkefølge: OppgavelisteSorteringRekkefølge = OppgavelisteSorteringRekkefølge.ASC,
        paging: Paging? = null,
        kunLedigeOppgaver: Boolean? = true,
        utvidetFilter: UtvidetOppgavelisteFilter? = null,
        sortBy: OppgavelisteSortering? = null,
    ): FinnOppgaverDto {
        val offset = if (paging != null) {
            (paging.side - 1) * paging.antallPerSide
        } else {
            0
        }
        val limit = paging?.antallPerSide ?: Int.MAX_VALUE // TODO: Fjern MAX_VALUE når vi har paging i FE
        val kunLedigeQuery =
            if (kunLedigeOppgaver == true) "AND RESERVERT_AV IS NULL AND PAA_VENT_TIL IS NULL" else ""
        val utvidetFilterQuery = if (utvidetFilter != null) utvidetFilterQuery(utvidetFilter) else ""
        val sortering = oppgaveSorteringQuery(
            sortBy = sortBy ?: OppgavelisteSortering.BEHANDLING_OPPRETTET,
        )
        val sorteringsRekkefølge = oppgaveRekkefølge(rekkefølge)

        val hentNesteOppgaveQuery = """
            SELECT o.*, m.markering_type
            FROM 
                OPPGAVE o
            LEFT JOIN MARKERING as m on o.behandling_ref = m.behandling_ref
            WHERE 
                ${filter.whereClause()} o.STATUS != 'AVSLUTTET' $utvidetFilterQuery $kunLedigeQuery
            ORDER BY ${sortering} ${sorteringsRekkefølge} 
            OFFSET $offset ROWS FETCH NEXT $limit ROWS ONLY
        """.trimIndent()

        val oppgaver = connection.queryList(hentNesteOppgaveQuery) {
            setRowMapper { oppgaveMapper(it) }
        }

        val countQuery = """
            SELECT COUNT(*) count FROM OPPGAVE o
            LEFT JOIN MARKERING as m on o.behandling_ref = m.behandling_ref
            WHERE ${filter.whereClause()} STATUS != 'AVSLUTTET' $utvidetFilterQuery $kunLedigeQuery
        """.trimIndent()

        val alleOppgaverCount = connection.queryFirstOrNull(countQuery) {
            setRowMapper { it.getInt("count") }
        } ?: 0
        val gjenstaaendeOppgaverAntall = maxOf(0, alleOppgaverCount - (offset + oppgaver.size))

        return FinnOppgaverDto(oppgaver, gjenstaaendeOppgaverAntall, alleOppgaverCount)
    }

    data class IdentMedOppgaveId(val ident: String, val oppgaveId: Long, val versjon: Long)
    data class FinnOppgaverDto(val oppgaver: List<OppgaveDto>, val antallGjenstaaende: Int, val antallTotalt: Int)

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
                RESERVERT_TIDSPUNKT = NULL,
                RESERVERT_AV_NAVN = NULL,
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
                UPPER(SAKSNUMMER) = ? AND STATUS != 'AVSLUTTET'
            ORDER BY BEHANDLING_OPPRETTET
        """.trimIndent()

        return connection.queryList(hentOppgaverGittSaksnummerQuery) {
            setParams {
                setString(1, saksnummer.uppercase())
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

    fun reserverOppgave(
        oppgaveId: OppgaveId,
        endretAvIdent: String,
        reservertAvIdent: String,
        reservertAvNavn: String?
    ) {
        val updaterOppgaveReservasjonQuery = """
            UPDATE 
                OPPGAVE 
            SET 
                RESERVERT_AV = ?,
                RESERVERT_AV_NAVN = ?,
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
                setString(2, reservertAvNavn)
                setString(3, endretAvIdent)
                setLong(4, oppgaveId.id)
                setLong(5, oppgaveId.versjon)
            }
            setResultValidator { require(it == 1) { "Prøvde å reservere én oppgave, men fant $it oppgaver. Oppgave: $oppgaveId" } }
        }
    }

    fun hentMineOppgaver(
        ident: String, paging: Paging? = null, kunPåVent: Boolean = false,
        sortBy: OppgavelisteSortering? = null,
        sortOrder: OppgavelisteSorteringRekkefølge? = null
    ): List<OppgaveDto> {
        val offset = if (paging != null) {
            (paging.side - 1) * paging.antallPerSide
        } else {
            0
        }
        val limit = paging?.antallPerSide ?: Int.MAX_VALUE // TODO: Fjern MAX_VALUE når vi har paging i FE

        val kunPåVentQuery = if (kunPåVent) " AND PAA_VENT_TIL IS NOT NULL" else ""
        val sortering = oppgaveSorteringQuery(
            sortBy = sortBy ?: OppgavelisteSortering.BEHANDLING_OPPRETTET,
        )
        val sorteringRekkefølge = oppgaveRekkefølge(sortOrder ?: OppgavelisteSorteringRekkefølge.ASC)

        val query = """
            SELECT $alleOppgaveFelt
            FROM OPPGAVE
            WHERE RESERVERT_AV = ?
              AND STATUS != 'AVSLUTTET' $kunPåVentQuery
            ORDER BY $sortering $sorteringRekkefølge
            OFFSET $offset ROWS FETCH NEXT $limit ROWS ONLY
        """.trimIndent()

        return connection.queryList(query) {
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

        return connection.queryList(query) {
            setRowMapper { row -> oppgaveMapper(row) }
        }
    }

    /**
     * Hent oppgaver som ikke er avsluttet.
     */
    fun hentÅpneOppgaver(avklaringsbehovReferanse: AvklaringsbehovReferanseDto): OppgaveId? {
        // TODO: Trenger bare behandlingsreferanse for å hente åpne oppgaver
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
                OppgaveId(row.getLong("ID"), row.getLong("VERSJON"))
            }
        }
        if (oppgaver.size != 1) {
            log.warn("Hent oppgave skal alltid returnere 1 oppgave. Kall med $avklaringsbehovReferanse fant ${oppgaver.size} oppgaver.")
        }
        return oppgaver.firstOrNull()
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
            sb.append("(VEILEDER_ARBEID = '$veileder' OR ")
            sb.append("VEILEDER_SYKDOM = '$veileder') AND ")
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
            enhet = row.getString("ENHET"),
            oppfølgingsenhet = row.getStringOrNull("OPPFOLGINGSENHET"),
            veilederArbeid = row.getStringOrNull("VEILEDER_ARBEID"),
            veilederSykdom = row.getStringOrNull("VEILEDER_SYKDOM"),
            behandlingOpprettet = row.getLocalDateTime("BEHANDLING_OPPRETTET"),
            avklaringsbehovKode = row.getString("AVKLARINGSBEHOV_TYPE"),
            status = Status.valueOf(row.getString("STATUS")),
            behandlingstype = Behandlingstype.valueOf(row.getString("BEHANDLINGSTYPE")),
            påVentTil = row.getLocalDateOrNull("PAA_VENT_TIL"),
            påVentÅrsak = row.getStringOrNull("PAA_VENT_AARSAK"),
            venteBegrunnelse = row.getStringOrNull("VENTE_BEGRUNNELSE"),
            årsakerTilBehandling = row.getArray("AARSAKER_TIL_BEHANDLING", String::class),
            vurderingsbehov = row.getArray("AARSAKER_TIL_BEHANDLING", String::class),
            reservertAv = row.getStringOrNull("RESERVERT_AV"),
            reservertAvNavn = row.getStringOrNull("RESERVERT_AV_NAVN"),
            reservertTidspunkt = row.getLocalDateTimeOrNull("RESERVERT_TIDSPUNKT"),
            opprettetAv = row.getString("OPPRETTET_AV"),
            opprettetTidspunkt = row.getLocalDateTime("OPPRETTET_TIDSPUNKT"),
            endretAv = row.getStringOrNull("ENDRET_AV"),
            endretTidspunkt = row.getLocalDateTimeOrNull("ENDRET_TIDSPUNKT"),
            versjon = row.getLong("VERSJON"),
            harFortroligAdresse = row.getBoolean("FORTROLIG_ADRESSE"),
            erSkjermet = row.getBoolean("ER_SKJERMET"),
            harUlesteDokumenter = row.getBoolean("ULESTE_DOKUMENTER"),
            årsakTilOpprettelse = row.getStringOrNull("AARSAK_TIL_OPPRETTELSE"),
            returStatus = row.getEnumOrNull<ReturStatus?, ReturStatus>("RETUR_AARSAK"),
            utløptVentefrist = row.getLocalDateOrNull("UTLOEPT_VENTEFRIST"),
            returInformasjon = row.getEnumOrNull<ReturStatus?, ReturStatus>("RETUR_AARSAK")?.let {
                ReturInformasjon(
                    status = it,
                    årsaker = row.getArray("RETUR_AARSAKER", String::class)
                        .map { årsak -> ÅrsakTilReturKode.valueOf(årsak) },
                    begrunnelse = row.getStringOrNull("RETUR_BEGRUNNELSE") ?: "",
                    endretAv = row.getStringOrNull("retur_returnert_av") ?: "UKJENT",
                )
            }
        )
    }

    private fun oppgaveSorteringQuery(sortBy: OppgavelisteSortering): String {
        return when (sortBy) {
            OppgavelisteSortering.PERSONIDENT -> "person_ident"
            OppgavelisteSortering.BEHANDLINGSTYPE -> "behandlingstype"
            OppgavelisteSortering.BEHANDLING_OPPRETTET -> "behandling_opprettet"
            OppgavelisteSortering.ÅRSAK_TIL_OPPRETTELSE -> "aarsak_til_opprettelse"
            OppgavelisteSortering.AVKLARINGSBEHOV_KODE -> "avklaringsbehov_type"
            OppgavelisteSortering.OPPRETTET_TIDSPUNKT -> "opprettet_tidspunkt"
        }
    }

    private fun oppgaveRekkefølge(sortOrder: OppgavelisteSorteringRekkefølge): Rekkefølge {
        return when (sortOrder) {
            OppgavelisteSorteringRekkefølge.ASC -> Rekkefølge.asc
            OppgavelisteSorteringRekkefølge.DESC -> Rekkefølge.desc
        }
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
            VEILEDER_ARBEID,
            VEILEDER_SYKDOM,
            BEHANDLING_OPPRETTET,
            AVKLARINGSBEHOV_TYPE,
            STATUS,
            BEHANDLINGSTYPE,
            PAA_VENT_TIL,
            PAA_VENT_AARSAK,
            VENTE_BEGRUNNELSE,
            RESERVERT_AV,
            RESERVERT_AV_NAVN,
            RESERVERT_TIDSPUNKT,
            OPPRETTET_AV,
            OPPRETTET_TIDSPUNKT,
            ENDRET_AV,
            ENDRET_TIDSPUNKT,
            VERSJON,
            AARSAKER_TIL_BEHANDLING,
            FORTROLIG_ADRESSE,
            ER_SKJERMET,
            ULESTE_DOKUMENTER,
            RETUR_AARSAK,
            RETUR_BEGRUNNELSE,
            retur_aarsaker,
            retur_returnert_av,
            AARSAK_TIL_OPPRETTELSE,
            UTLOEPT_VENTEFRIST
        """.trimIndent()
    }

}