package no.nav.aap.oppgave.mottattdokument

import java.time.LocalDateTime
import java.util.*


data class MottattDokument(
    val type: String,
    val behandlingRef: UUID,
    val referanse: String,
    val opprettetTidspunkt: LocalDateTime? = null,
)