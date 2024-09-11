package no.nav.aap.oppgave.opprett

import java.time.LocalDateTime
import java.util.UUID

@JvmInline
value class Saksnummer(private val saksnummer: String) {
    override fun toString(): String {
        return saksnummer
    }
}

@JvmInline
value class BehandlingRef(val uuid: UUID)


@JvmInline
value class AvklaringsbehovKode(val kode: String)


data class AvklaringsbehovRequest(
    val saksnummer: Saksnummer,
    val behandlingRef: BehandlingRef,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: AvklaringsbehovKode,
)