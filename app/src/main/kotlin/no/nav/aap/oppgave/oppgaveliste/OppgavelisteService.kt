package no.nav.aap.oppgave.oppgaveliste

import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.miljo.MiljøKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.OppgaveRepository.FinnOppgaverDto
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.enhet.NAY_ENHETER
import no.nav.aap.oppgave.enhet.OppgaveEnhetDto
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.liste.OppgaveSorteringFelt
import no.nav.aap.oppgave.liste.OppgaveSorteringRekkefølge
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.liste.UtvidetOppgavelisteFilter
import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.markering.tilDto
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import java.util.UUID

const val maksOppgaver = 50

class OppgavelisteService(
    private val oppgaveRepository: OppgaveRepository,
    private val markeringRepository: MarkeringRepository
) {
    fun søkEtterOppgaver(søketekst: String): List<OppgaveDto> {
        val oppgaver = if (søketekst.length >= 11) {
            oppgaveRepository.finnOppgaverGittPersonident(søketekst)
        } else {
            oppgaveRepository.finnOppgaverGittSaksnummer(søketekst)
        }

        return oppgaver.map { oppgave ->
            val behandlingRef = requireNotNull(oppgave.behandlingRef) {
                "Fant ikke behandlingsreferanse for oppgave med id ${oppgave.id}"
            }
            val markeringer = markeringRepository.hentMarkeringerForBehandling(behandlingRef)
            oppgave.leggPåMarkeringer(markeringer.tilDto())
        }
    }

    fun hentAktivOppgave(behandlingReferanse: BehandlingReferanse): OppgaveDto? {
        val oppgave = oppgaveRepository.hentAktivOppgave(behandlingReferanse)
        if (oppgave != null) {
            val markeringer = markeringRepository.hentMarkeringerForBehandling(behandlingReferanse.referanse)
            return oppgave.leggPåMarkeringer(markeringer.tilDto())
        }
        return oppgave
    }

    fun hentOppgaveEnhetListe(behandlingReferanse: BehandlingReferanse): List<OppgaveEnhetDto> {
        val oppgaver = oppgaveRepository.hentOppgaver(behandlingReferanse.referanse)
        return oppgaver.map { oppgave ->
            val enhet = oppgave.oppfølgingsenhet ?: oppgave.enhet
            OppgaveEnhetDto(
                avklaringsbehovKode = oppgave.avklaringsbehovKode,
                enhet = enhet,
                erNayEnhet = NAY_ENHETER.map { it.kode }.contains(enhet)
            )
        }
    }

    fun hentOppgaverMedTilgang(
        enhetService: EnhetService,
        utvidetFilter: UtvidetOppgavelisteFilter?,
        enheter: Set<String>,
        paging: Paging,
        kunLedigeOppgaver: Boolean,
        filter: FilterDto,
        veilederIdent: String?,
        token: OidcToken,
        ident: String,
        sortBy: OppgaveSorteringFelt?,
        sortOrder: OppgaveSorteringRekkefølge?
    ): FinnOppgaverDto {
        val sortOrderMedDefault = if (sortOrder != null) {
            sortOrder
        } else {
            when (Miljø.er()) {
                MiljøKode.DEV -> OppgaveSorteringRekkefølge.DESC
                else -> OppgaveSorteringRekkefølge.ASC
            }
        }

        val kombinertFilter = settFilter(filter, utvidetFilter)
        val finnOppgaverDto =
            oppgaveRepository.finnOppgaver(
                filter =
                    kombinertFilter.copy(
                        enheter = enheter,
                        veileder = veilederIdent
                    ),
                rekkefølge = sortOrderMedDefault,
                paging = paging,
                kunLedigeOppgaver = kunLedigeOppgaver,
                utvidetFilter = utvidetFilter,
                sortBy = sortBy,
            )

        val oppgaver =
            finnOppgaverDto.oppgaver.map { oppgave ->
                val behandlingRef = requireNotNull(oppgave.behandlingRef) {
                    "Fant ikke behandlingsreferanse for oppgave med id ${oppgave.id}"
                }
                val markeringer = markeringRepository.hentMarkeringerForBehandling(behandlingRef)
                oppgave.leggPåMarkeringer(markeringer.tilDto())
            }

        return FinnOppgaverDto(
            oppgaver = oppgaver.filtrerPåTilgang(enhetService, token, ident),
            antallGjenstaaende = finnOppgaverDto.antallGjenstaaende,
            antallTotalt = finnOppgaverDto.antallTotalt,
        )
    }

    fun hentMineOppgaver(
        ident: String,
        kunPaaVent: Boolean?,
        sortBy: OppgaveSorteringFelt?,
        sortOrder: OppgaveSorteringRekkefølge?
    ): List<OppgaveDto> =
        oppgaveRepository.hentMineOppgaver(
            ident = ident,
            kunPåVent = kunPaaVent == true,
            sortBy = sortBy,
            sortOrder = sortOrder
        ).map {
            it.leggPåMarkeringer(
                markeringRepository.hentMarkeringerForBehandling(requireNotNull(it.behandlingRef) {
                    "Fant ikke behandlingsreferanse for oppgave med id ${it.id}"
                }).tilDto()
            )
        }.hentPersonNavn()

    fun hentOppgaverForBehandling(referanse: UUID): List<OppgaveDto> {
        return oppgaveRepository.hentOppgaver(referanse)
    }

    private fun settFilter(
        filter: FilterDto,
        utvidetFilter: UtvidetOppgavelisteFilter?
    ): FilterDto {
        if (utvidetFilter == null) return filter
        return filter.copy(
            behandlingstyper = utvidetFilter.behandlingstyper,
            avklaringsbehovKoder = utvidetFilter.avklaringsbehovKoder
        )
    }

    private fun OppgaveDto.leggPåMarkeringer(markeringer: List<MarkeringDto>): OppgaveDto =
        this.copy(markeringer = markeringer)

    private fun List<OppgaveDto>.filtrerPåTilgang(
        enhetService: EnhetService,
        token: OidcToken,
        ident: String
    ): List<OppgaveDto> {
        val oppgaverFiltrertForKode7 = sjekkTilgangTilFortroligAdresse(enhetService, ident, token, this)
        val enhetsGrupper = enhetService.hentEnheter(ident, token)
        return oppgaverFiltrertForKode7
            .asSequence()
            .filter { it.enhetForKø in enhetsGrupper }
            .take(maksOppgaver)
            .toList()
    }

    private fun sjekkTilgangTilFortroligAdresse(
        enhetService: EnhetService,
        ident: String,
        token: OidcToken,
        oppgaver: List<OppgaveDto>
    ): List<OppgaveDto> =
        if (!enhetService.kanSaksbehandleFortroligAdresse(ident, token)) {
            oppgaver.filterNot { it.harFortroligAdresse == true }
        } else {
            oppgaver
        }
}
