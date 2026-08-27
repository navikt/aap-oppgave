package no.nav.aap.oppgave.liste

import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.ForrigeKvalitetssikrerDto
import no.nav.aap.oppgave.ReturInformasjonDto
import no.nav.aap.oppgave.TilbakekrevingsVarsDto
import no.nav.aap.oppgave.Uførevedtakinfo
import no.nav.aap.oppgave.enhet.EnhetDto
import no.nav.aap.oppgave.hent.SkjermingInfoResponse
import no.nav.aap.oppgave.hent.VenteInformasjonResponse
import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import java.time.LocalDateTime

data class OppgavelisteRespons(
    val antallTotalt: Int,
    val oppgaver: List<OppgaveMedKontekstResponse>,
    val antallGjenstaaende: Int? = null,
    val sattFilterBehandlingstyper: Set<Behandlingstype>? = emptySet(),
)

data class OppgaveMedKontekstResponse(
    val behandlingOpprettet: LocalDateTime,
    val avklaringsbehovKode: String,
    val vurderingsbehov: List<String>,
    val årsakTilOpprettelse: String?,
    val oppgaveMetadata: OppgaveMetadataResponse,
    val behandlingskontekst: BehandlingskontekstResponse,
    val personOgEnhet: PersonOgEnhetResponse,
    val oppgavelisteTags: OppgavelisteTagsResponse,
    val veilederArbeid: String?,
    val veilederSykdom: String?,
    val reservertAv: String?,
    val reservertAvNavn: String?,
    val tilbakekrevingsVars: TilbakekrevingsVarsDto?,
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
    val skjermingInfo: SkjermingInfoResponse,
    val harUlesteDokumenter: Boolean?,
    val markeringer: List<MarkeringDto>,
    val forrigeKvalitetssikrerInfo: ForrigeKvalitetssikrerDto?,
    val uføreVedtak: Uførevedtakinfo?,
)