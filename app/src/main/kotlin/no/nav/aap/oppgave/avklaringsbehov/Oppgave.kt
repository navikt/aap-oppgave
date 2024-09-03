package no.nav.aap.oppgave.avklaringsbehov

import java.time.LocalDateTime

data class Oppgave(
    val id: OppgaveId,
    val saksnummer: Saksnummer,
    val behandlingRef: BehandlingRef,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovType: AvklaringsbehovType,
    val avklaringsbehovStatus: AvklaringsbehovStatus,
    val avklaresAv: AvklaringsbehovType,
    val navKontor: Navkontor? = null,
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
)
