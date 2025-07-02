package no.nav.aap.oppgave.mottattdokument

import java.util.*


data class MottattDokument(
    val type: String,
    val behandlingRef: UUID,
    val referanse: String,
)