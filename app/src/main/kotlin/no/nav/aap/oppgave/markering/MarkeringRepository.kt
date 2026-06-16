package no.nav.aap.oppgave.markering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.dbconnect.Row
import no.nav.aap.oppgave.verdityper.MarkeringHendelseType
import java.util.UUID

class MarkeringRepository(
    private val connection: DBConnection
) {

    fun oppdaterMarkering(referanse: UUID, markering: BehandlingMarkering) {
        // Kan bare ha én markering av gitt type på en behandling
        val markeringSkalOverskrives =
            hentMarkeringerForBehandling(referanse).any { it.markeringType == markering.markeringType }

        if (markeringSkalOverskrives) {
            slettMarkering(referanse, markering)
        }
        lagreMarkering(referanse, markering)
    }

    private fun lagreMarkering(
        referanse: UUID,
        markering: BehandlingMarkering
    ) {
        val query =
            """
            INSERT INTO MARKERING (behandling_ref, markering_type, begrunnelse, opprettet_av, opprettet_av_navn)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

        connection.execute(query) {
            setParams {
                setUUID(1, referanse)
                setEnumName(2, markering.markeringType)
                setString(3, markering.begrunnelse)
                setString(4, markering.opprettetAv)
                setString(5, markering.opprettetAvNavn)
            }
        }
    }

    fun lagreMarkeringMedHendelse(
        referanse: UUID,
        markering: BehandlingMarkering,
    ) {
        val query =
            """
            INSERT INTO MARKERING (behandling_ref, markering_type, begrunnelse, opprettet_av, opprettet_av_navn, hendelse_type)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

        connection.execute(query) {
            setParams {
                setUUID(1, referanse)
                setEnumName(2, markering.markeringType)
                setString(3, markering.begrunnelse)
                setString(4, markering.opprettetAv)
                setString(5, markering.opprettetAvNavn)
                setEnumName(6, markering.hendelseType)
            }
        }
    }

    fun hentMarkeringerForBehandling(referanse: UUID): List<BehandlingMarkering> {
        val query =
            """
            SELECT DISTINCT ON (markering_type) *
            FROM MARKERING
            WHERE behandling_ref = ?
              AND (hendelse_type = 'OPPRETTET' OR hendelse_type IS NULL)
            ORDER BY markering_type, opprettet_tid DESC
            """.trimIndent()

        val markeringer =
            connection.queryList(query) {
                setParams {
                    setUUID(1, referanse)
                }
                setRowMapper {
                    markeringMapper(it)
                }
            }
        return markeringer
    }

    fun slettMarkering(
        referanse: UUID,
        markering: BehandlingMarkering
    ) {
        val query =
            """
            DELETE FROM MARKERING WHERE behandling_ref = ? AND markering_type = ?
            """.trimIndent()

        connection.execute(query) {
            setParams {
                setUUID(1, referanse)
                setEnumName(2, markering.markeringType)
            }
        }
    }

    private fun markeringMapper(row: Row): BehandlingMarkering =
        BehandlingMarkering(
            markeringType = row.getEnum("markering_type"),
            begrunnelse = row.getStringOrNull("begrunnelse"),
            opprettetAv = row.getString("opprettet_av"),
            opprettetAvNavn = row.getStringOrNull("opprettet_av_navn"),
            hendelseType = row.getEnumOrNull<MarkeringHendelseType?, MarkeringHendelseType>("hendelse_type"),
            opprettetTidspunkt = row.getLocalDateTimeOrNull("opprettet_tid"),
        )
}