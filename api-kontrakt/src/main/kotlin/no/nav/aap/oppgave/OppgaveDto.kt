package no.nav.aap.oppgave

import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * @param enhet Enhetsnummeret til enheten som er koblet til oppgaven.
 * TODO: Fjern sensitive felter
 */
data class OppgaveDto(
    val id: Long? = null,
    val personIdent: String? = null,
    val personNavn: String? = null,
    val saksnummer: String? = null,
    val behandlingRef: UUID? = null,
    val journalpostId: Long? = null,
    val enhet: String,
    val oppfølgingsenhet: String?,
    val veilederArbeid: String? = null,
    val veilederSykdom: String? = null,
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: String,
    val status: Status = Status.OPPRETTET,
    val behandlingstype: Behandlingstype,
    val påVentTil: LocalDate? = null,
    val påVentÅrsak: String? = null,
    val venteBegrunnelse: String? = null,
    val årsakerTilBehandling: List<String> = emptyList(),
    val reservertAv: String? = null,
    val reservertTidspunkt: LocalDateTime? = null,
    val opprettetAv: String,
    val opprettetTidspunkt: LocalDateTime,
    val endretAv: String? = null,
    val endretTidspunkt: LocalDateTime? = null,
    val versjon: Long = 0
) {
    init {
        if (journalpostId == null) {
            if (saksnummer == null || behandlingRef == null) {
                throw IllegalArgumentException("Saksnummer og behandlingRef kan ikke være null dersom journalpostId er null")
            }
        }
    }

    fun tilAvklaringsbehovReferanseDto(): AvklaringsbehovReferanseDto {
        return AvklaringsbehovReferanseDto(
            this.saksnummer,
            this.behandlingRef,
            this.journalpostId,
            this.avklaringsbehovKode
        )
    }

    fun enhetForKø(): String {
        return this.oppfølgingsenhet ?: this.enhet
    }

}
