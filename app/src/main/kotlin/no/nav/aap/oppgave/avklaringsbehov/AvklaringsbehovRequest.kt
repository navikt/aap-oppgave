package no.nav.aap.oppgave.avklaringsbehov

import java.time.LocalDateTime

@JvmInline
value class Saksnummer(val saksnummer: String)

@JvmInline
value class BehandlingRef(val behandlingRef: String)

enum class Sakstype {
    FØRSTEGANGSBEHANDLING,
    REVURDERING,
    KLAGE,
    TILBAKEKREVING
}

enum class AvklaringsbehovType(val kode: String) {
    AVKLAR_STUDENT("5001"),
    AVKLAR_SYKDOM("5003"),
    FASTSETT_ARBEIDSEVNE("5004"),
    FRITAK_MELDEPLIKT("5005"),
    AVKLAR_BISTANDSBEHOV("5006"),
    VURDER_SYKEPENGEERSTATNING("5007"),
    FASTSETT_BEREGNINGSTIDSPUNKT("5008"),
    FORESLÅ_VEDTAK("5098"),
    FATTE_VEDTAK("5099"),
}

enum class AvklaringsbehovStatus {
    OPPRETTET,
    AVSLUTTET
}

enum class AvklaresAv {
    NAVKONTOR,
    NAY
}

@JvmInline
value class Navkontor(val kommunenr: String)


data class AvklaringsbehovRequest(
    val saksnummer: Saksnummer,
    val behandlingRef: BehandlingRef,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovType: AvklaringsbehovType,
    val avklaringsbehovStatus: AvklaringsbehovStatus,
    val avklaresAv: AvklaringsbehovType,
    val navKontor: Navkontor? = null,
)