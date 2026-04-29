package no.nav.aap.oppgave.oppdater.hendelse

enum class AvklaringsbehovStatus {
    OPPRETTET,
    AVSLUTTET,
    TOTRINNS_VURDERT,
    SENDT_TILBAKE_FRA_BESLUTTER,
    KVALITETSSIKRET,
    SENDT_TILBAKE_FRA_KVALITETSSIKRER,
    AVBRUTT;

    fun erÅpent(): Boolean {
        return this in setOf(
            OPPRETTET,
            SENDT_TILBAKE_FRA_BESLUTTER,
            SENDT_TILBAKE_FRA_KVALITETSSIKRER
        )
    }
}

val ÅPNE_STATUSER = setOf(
    AvklaringsbehovStatus.OPPRETTET,
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER,
    AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
)

val AVSLUTTEDE_STATUSER = setOf(
    AvklaringsbehovStatus.AVSLUTTET,
    AvklaringsbehovStatus.AVBRUTT,
    AvklaringsbehovStatus.KVALITETSSIKRET,
    AvklaringsbehovStatus.TOTRINNS_VURDERT,
)


enum class BehandlingStatus {
    ÅPEN,
    LUKKET
}
