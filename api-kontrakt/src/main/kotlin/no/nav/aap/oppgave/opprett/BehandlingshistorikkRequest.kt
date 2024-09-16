package no.nav.aap.oppgave.opprett

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDateTime

//TODO skal ligge behandlingsflyt.api-kontrakt, fjernes når det er klart
data class BehandlingshistorikkRequest(
    val personident: String,
    val saksnummer: String,
    val referanse: String,
    val behandlingType: Behandlingstype,
    val status: Behandlingstatus,
    val opprettetTidspunkt: LocalDateTime,
    val avklaringsbehov: List<AvklaringsbehovDto>,
) {
    fun erLukket() =
        (status == Behandlingstatus.AVSLUTTET) ||
                avklaringsbehov.all { it.status == Avklaringsbehovstatus.AVSLUTTET }

    @JsonIgnore
    fun getÅpentAvklaringsbehov() = avklaringsbehov.firstOrNull {
        it.status in setOf(
            Avklaringsbehovstatus.OPPRETTET,
            Avklaringsbehovstatus.SENDT_TILBAKE_FRA_BESLUTTER,
            Avklaringsbehovstatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER
        )
    }
}

enum class Behandlingstype {
    Førstegangsbehandling,
    Revurdering,
    Tilbakekreving,
    Klage
}

enum class Behandlingstatus {
    OPPRETTET,
    UTREDES,
    AVSLUTTET,
    PÅ_VENT
}

enum class Avklaringsbehovstatus {
    OPPRETTET,
    AVSLUTTET,
    KVALITETSSIKRET,
    SENDT_TILBAKE_FRA_KVALITETSSIKRER,
    TOTRINNS_VURDERT,
    SENDT_TILBAKE_FRA_BESLUTTER,
    AVBRUTT
}

enum class Avklaringsbehovtype(val kode: String) {
    MANUELT_SATT_PÅ_VENT("9001"),
    AVKLAR_STUDENT("5001"),
    AVKLAR_SYKDOM("5003"),
    FASTSETT_ARBEIDSEVNE("5004"),
    FRITAK_MELDEPLIKT("5005"),
    AVKLAR_BISTANDSBEHOV("5006"),
    VURDER_SYKEPENGEERSTATNING("5007"),
    FASTSETT_BEREGNINGSTIDSPUNKT("5008"),
    AVKLAR_BARN("5009"),
    AVKLAR_SONINGSFORRHOLD("5010"),
    AVKLAR_HELSEINSTITUSJON("5011"),
    KVALITETSSIKRING("5097"),
    FORESLÅ_VEDTAK("5098"),
    FATTE_VEDTAK("5099");

    companion object {
        private val map = entries.associateBy(Avklaringsbehovtype::kode)
        fun fraKode(kode: String) =
            map[kode] ?: throw IllegalArgumentException("Finner ikke Avklaringsbehovtype for kode: $kode")
    }
}

data class AvklaringsbehovDto(
    val definisjon: Definisjon,
    val status: Avklaringsbehovstatus,
    val endringer: List<AvklaringsbehovhendelseEndring>
) {
    fun getOpprettelsestidspunkt() = endringer.find { it.status == Avklaringsbehovstatus.OPPRETTET }?.tidsstempel
        ?: throw IllegalArgumentException("Avklaringsbehov mangler ")
}

data class Definisjon(
    val type: String
)

data class AvklaringsbehovhendelseEndring(
    val status: Avklaringsbehovstatus,
    val tidsstempel: LocalDateTime,
    val endretAv: String
)
