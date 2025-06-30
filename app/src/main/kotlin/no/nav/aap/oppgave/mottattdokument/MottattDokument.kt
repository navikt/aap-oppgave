package no.nav.aap.oppgave.mottattdokument

import java.util.*


data class MottattDokument(
    val type: MottattDokumentType,
    val behandlingRef: UUID,
    val referanse: String,
)

enum class MottattDokumentType {
    LEGEERKLÆRING;
}