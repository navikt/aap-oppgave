package no.nav.aap.oppgave.markering

import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import no.nav.aap.oppgave.verdityper.MarkeringHendelseType
import java.time.LocalDateTime

data class MarkeringDto(
    val markeringType: MarkeringForBehandling,
    val begrunnelse: String? = null,
    val opprettetAv: String? = null,
    val opprettetTidspunkt: LocalDateTime,
    val opprettetAvNavn: String? = null,
    val hendelseType: MarkeringHendelseType? = null,
)