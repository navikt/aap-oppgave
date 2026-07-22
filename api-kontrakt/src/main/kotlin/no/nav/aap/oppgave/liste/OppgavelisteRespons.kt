package no.nav.aap.oppgave.liste

import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.ForrigeKvalitetssikrerInfo
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.ReturInformasjonDto
import no.nav.aap.oppgave.TilbakekrevingsVarsDto
import no.nav.aap.oppgave.enhet.EnhetDto
import no.nav.aap.oppgave.hent.SkjermingInfoResponse
import no.nav.aap.oppgave.hent.VenteInformasjonResponse
import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDateTime

data class OppgavelisteRespons(
    val antallTotalt: Int,
    val oppgaver: List<OppgaveDto>,
    val antallGjenstaaende: Int? = null,
    val sattFilterBehandlingstyper: Set<Behandlingstype>? = emptySet(),
)

data class OppgavelisteResponsV2(
    val antallTotalt: Int,
    val oppgaver: List<ListeOppgaveResponse>,
    val antallGjenstaaende: Int? = null,
    val sattFilterBehandlingstyper: Set<Behandlingstype>? = emptySet(),
)

data class ListeOppgaveResponse(
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: String,
    val vurderingsbehov: List<String>,
    val årsakTilOpprettelse: String?,
    val oppgaveMetadataResponse: OppgaveMetadataResponse,
    val behandlingskontekstResponse: BehandlingskontekstResponse,
    val personOgEnhetResponse: PersonOgEnhetResponse,
    val oppgavelisteTagsResponse: OppgavelisteTagsResponse,
    val veilederArbeid: String?,
    val veilederSykdom: String?,
    val reservertAv: String?,
    val reservertAvNavn: String?,
    val tilbakekrevingsVarsDto: TilbakekrevingsVarsDto?,
)

data class OppgaveMetadataResponse(
    val id: Long,
    val versjon: Long,
    val status: Status,
    val opprettetTidspunkt: LocalDateTime,
)

data class PersonOgEnhetResponse(
    val personIdent: String,
    val personNavn: String?,
    val enhet: String,
    val oppfølgingsenhet: String?,
    val enhetForrigeOppgave: EnhetDto?,
)

data class OppgavelisteTagsResponse(
    val påVentInfo: VenteInformasjonResponse?,
    val forrigePåVentInfo: VenteInformasjonResponse?,
    val returInformasjon: ReturInformasjonDto?,
    val skjermingInfoResponse: SkjermingInfoResponse,
    val harUlesteDokumenter: Boolean?,
    val markeringer: List<MarkeringDto>,
    val forrigeKvalitetssikrerInfo: ForrigeKvalitetssikrerInfo?,
)