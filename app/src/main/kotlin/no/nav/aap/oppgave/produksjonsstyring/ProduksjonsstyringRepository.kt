package no.nav.aap.oppgave.produksjonsstyring

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.verdityper.Behandlingstype


data class AvklaringbehovOgAntall(
    val avklaringsbehovtype: AvklaringsbehovKode,
    val antall: Int
)

class ProduksjonsstyringRepository(private val connection: DBConnection) {

    fun hentAntallÅpneOppgaver(behandlingstype: Behandlingstype? = null): Map<AvklaringsbehovKode, Int> {
        val behandlingstypeClause = if (behandlingstype != null) "AND BEHANDLINGSTYPE = ?" else ""

        val sql = """
            SELECT 
                AVKLARINGSBEHOV_TYPE, COUNT(*) ANTALL
            FROM 
                OPPGAVE
            WHERE 
                STATUS != 'AVSLUTTET'
                $behandlingstypeClause
            GROUP BY 
                AVKLARINGSBEHOV_TYPE
        """.trimIndent()

        val antallList =  connection.queryList<AvklaringbehovOgAntall>(sql) {
            setParams {
                if (behandlingstype != null) {
                    setString(1, behandlingstype.name)
                }
            }
            setRowMapper { row ->
                AvklaringbehovOgAntall(AvklaringsbehovKode(row.getString("AVKLARINGSBEHOV_TYPE")), row.getInt("ANTALL"))
            }
        }
        return antallList.map {it.avklaringsbehovtype to it.antall}.toMap()
    }

}