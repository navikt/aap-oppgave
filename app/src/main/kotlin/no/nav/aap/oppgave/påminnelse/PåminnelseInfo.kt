package no.nav.aap.oppgave.påminnelse

import java.time.LocalDate
import java.time.LocalDateTime

data class PåminnelseInfo(
    val status: PåminnelseStatus,
    val påminnelseDato: LocalDate,
    val opprettetTidspunkt: LocalDateTime,
)

enum class PåminnelseStatus {
    PLANLAGT,
    SENDT,
    AVBRUTT
}