package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as BehandlingStatus
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.behandling.ÅrsakTilOpprettelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.MottattDokumentDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilRetur
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.ÅrsakTilSettPåVent
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID


fun behandlingFlytHendelse(
    saksnummer: Saksnummer = Saksnummer("123"),
    referanse: BehandlingReferanse = BehandlingReferanse(UUID.randomUUID()),
    personIdent: String = "12345678901",
    status: BehandlingStatus = BehandlingStatus.UTREDES,
    behandlingType: TypeBehandling = TypeBehandling.Førstegangsbehandling,
    erPåVent: Boolean = false,
    hendelsesTidspunkt: LocalDateTime = LocalDateTime.now(),
    opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),
    reserverTil: String? = null,
    årsakTilOpprettelse: ÅrsakTilOpprettelse = ÅrsakTilOpprettelse.SØKNAD,
    relevanteIdenterPåBehandling: List<String> = emptyList(),
    vurderingsbehov: List<String> = listOf("SØKNAD"),
    block: BehandlingFlytHendelseBuilder.() -> Unit = {}
): BehandlingFlytStoppetHendelse {
    val builder = BehandlingFlytHendelseBuilder().apply(block)
    return BehandlingFlytStoppetHendelse(
        personIdent = personIdent,
        saksnummer = saksnummer,
        referanse = referanse,
        status = status,
        opprettetTidspunkt = opprettetTidspunkt,
        behandlingType = behandlingType,
        versjon = "Kelvin 1.0",
        hendelsesTidspunkt = hendelsesTidspunkt,
        erPåVent = erPåVent,
        mottattDokumenter = builder.mottattDokumenter,
        reserverTil = reserverTil,
        årsakerTilBehandling = listOf(),
        avklaringsbehov = builder.avklaringsbehov,
        vurderingsbehov = vurderingsbehov,
        årsakTilOpprettelse = årsakTilOpprettelse,
        relevanteIdenterPåBehandling = relevanteIdenterPåBehandling,
    )
}

class BehandlingFlytHendelseBuilder {
    val avklaringsbehov = mutableListOf<AvklaringsbehovHendelseDto>()
    val mottattDokumenter = mutableListOf<MottattDokumentDto>()

    fun avklaringsbehov(
        definisjon: Definisjon,
        status: Status,
        block: AvklaringsbehovBuilder.() -> Unit = {}
    ) {
        val builder = AvklaringsbehovBuilder().apply(block)
        avklaringsbehov += AvklaringsbehovHendelseDto(
            avklaringsbehovDefinisjon = definisjon,
            status = status,
            endringer = builder.endringer
        )
    }
}

class AvklaringsbehovBuilder {
    val endringer = mutableListOf<EndringDTO>()

    fun endring(
        status: Status,
        endretAv: String,
        tidsstempel: LocalDateTime,
        begrunnelse: String? = null,
        årsakTilRetur: List<ÅrsakTilRetur>? = null,
        frist: LocalDate? = null,
        årsakTilSettPåVent: ÅrsakTilSettPåVent? = null

    ) {
        endringer += EndringDTO(
            status = status,
            endretAv = endretAv,
            tidsstempel = tidsstempel,
            begrunnelse = begrunnelse,
            årsakTilRetur = årsakTilRetur ?: emptyList(),
            frist = frist,
            årsakTilSattPåVent = årsakTilSettPåVent,
        )
    }
}