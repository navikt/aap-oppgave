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

    fun erAvsluttet(): Boolean {
        return this in setOf(
            AVSLUTTET,
            AVBRUTT,
            KVALITETSSIKRET,
            TOTRINNS_VURDERT
        )
    }

    fun harBlittSendtTilbakeFraTotrinn(): Boolean {
        return this in setOf(
            SENDT_TILBAKE_FRA_BESLUTTER,
            SENDT_TILBAKE_FRA_KVALITETSSIKRER
        )
    }
}

enum class BehandlingStatus {
    ÅPEN,
    LUKKET
}
