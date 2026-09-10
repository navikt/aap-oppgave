package no.nav.aap.oppgave.oppgaveliste

import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.miljo.MiljøKode
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.OppgaveRepository.FinnOppgaverDto
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.enhet.OppgaveEnhetDto
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.liste.OppgaveSorteringFelt
import no.nav.aap.oppgave.liste.OppgaveSorteringFelt.TILBAKEKREVINGS_BELOP
import no.nav.aap.oppgave.liste.OppgaveSorteringRekkefølge
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.liste.UtvidetOppgavelisteFilter
import no.nav.aap.oppgave.markering.Markering
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.uføreVedtak.UføreVedtak
import no.nav.aap.oppgave.uføreVedtak.UføreVedtakRepository
import java.util.UUID

const val maksOppgaver = 50

class OppgavelisteService(
    private val oppgaveRepository: OppgaveRepository,
    private val markeringRepository: MarkeringRepository,
    private val uføreVedtakRepository: UføreVedtakRepository,
    private val enhetService: EnhetService,
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService(),
) {
    fun søkEtterOppgaver(søketekst: String): List<Oppgave> {
        val oppgaver = if (søketekst.all { it.isDigit() }) {
            oppgaveRepository.finnÅpneOppgaverGittPersonident(søketekst)
        } else {
            oppgaveRepository.finnOppgaverGittSaksnummer(søketekst)
        }

        return oppgaver.map { oppgave ->
            val markeringer = markeringRepository.hentGjeldendeMarkeringerForBehandling(oppgave.behandlingRef)
            val uførevedtak = uføreVedtakRepository.hentAktiveUføreVedtakForBehandling(oppgave.behandlingRef)
            oppgave.leggPåMarkeringer(markeringer).leggPåUføreVedtak(uførevedtak)
        }
    }

    fun hentAktivOppgave(behandlingReferanse: BehandlingReferanse): Oppgave? {
        val oppgave = oppgaveRepository.hentAktivOppgave(behandlingReferanse)
        if (oppgave != null) {
            val markeringer = markeringRepository.hentGjeldendeMarkeringerForBehandling(behandlingReferanse.referanse)
            val uførevedtak = uføreVedtakRepository.hentAktiveUføreVedtakForBehandling(behandlingReferanse.referanse)
            return oppgave.leggPåUføreVedtak(uførevedtak).leggPåMarkeringer(markeringer)
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
            )
        }
    }
    
    fun hentOppgaverMedTilgang(
        utvidetFilter: UtvidetOppgavelisteFilter?,
        enheter: Set<String>,
        paging: Paging,
        kunLedigeOppgaver: Boolean,
        filter: Filter,
        veilederIdent: String?,
        token: OidcToken,
        ident: String,
        sortBy: OppgaveSorteringFelt?,
        sortOrder: OppgaveSorteringRekkefølge?,
        hastemarkeringerFørst: Boolean
    ): FinnOppgaverDto {
        val sortOrderMedDefault = sortOrder
            ?: when (Miljø.er()) {
                MiljøKode.DEV -> OppgaveSorteringRekkefølge.DESC
                else -> OppgaveSorteringRekkefølge.ASC
            }

        val kombinertFilter = validerOgKombinerFiltre(filter, utvidetFilter) ?: return FinnOppgaverDto(
            oppgaver = emptyList(),
            antallGjenstaaende = 0,
            antallTotalt = 0
        )

        if (enheter.isEmpty()) {
            return FinnOppgaverDto(
                oppgaver = emptyList(),
                antallGjenstaaende = 0,
                antallTotalt = 0
            )
        }

        val aktivSortering = toggleAktivSortering(sortBy)

        val finnOppgaverDto = oppgaveRepository.finnOppgaver(
            filter = kombinertFilter.copy(
                enheter = enheter,
                veileder = veilederIdent
            ),
            rekkefølge = sortOrderMedDefault,
            paging = paging,
            kunLedigeOppgaver = kunLedigeOppgaver,
            utvidetFilter = utvidetFilter,
            sortBy = aktivSortering,
            enheterMedNavn = enhetService.hentEnheterMedNavn().takeIf { filter.navn == "Kvalitetssikrer" }.orEmpty(),
            hastemarkeringerFørst = hastemarkeringerFørst
        )

        val oppgaver =
            finnOppgaverDto.oppgaver.map { oppgave ->
                val behandlingRef = oppgave.behandlingRef
                val markeringer = markeringRepository.hentGjeldendeMarkeringerForBehandling(behandlingRef)
                val uføreVedtak = uføreVedtakRepository.hentAktiveUføreVedtakForBehandling(behandlingRef)
                oppgave.leggPåMarkeringer(markeringer).leggPåUføreVedtak(uføreVedtak)
            }

        return FinnOppgaverDto(
            oppgaver = oppgaver.filtrerPåTilgang(token, ident),
            antallGjenstaaende = finnOppgaverDto.antallGjenstaaende,
            antallTotalt = finnOppgaverDto.antallTotalt,
        )
    }

    fun hentMineOppgaver(
        ident: String,
        kunPaaVent: Boolean?,
        sortBy: OppgaveSorteringFelt?,
        sortOrder: OppgaveSorteringRekkefølge?,
    ): List<Oppgave> {

        val aktivSortering = toggleAktivSortering(sortBy)

        val oppgaver = oppgaveRepository.hentMineOppgaver(
            ident = ident,
            kunPåVent = kunPaaVent == true,
            sortBy = aktivSortering,
            sortOrder = sortOrder
        ).map {
            it.leggPåMarkeringer(
                markeringRepository.hentGjeldendeMarkeringerForBehandling(it.behandlingRef)
            ).leggPåUføreVedtak(
                uføreVedtakRepository.hentAktiveUføreVedtakForBehandling(it.behandlingRef)
            )

        }.hentPersonNavn()

        val (medMarkering, utenMarkering) = oppgaver.partition { it.markeringer.isNotEmpty() }
        return medMarkering + utenMarkering
    }

    private fun toggleAktivSortering(sortBy: OppgaveSorteringFelt?): OppgaveSorteringFelt? {
        val aktivBelopSortering = unleashService.isEnabled(FeatureToggles.SorterOppgavelistePaBelop)
        return if (sortBy == TILBAKEKREVINGS_BELOP && !aktivBelopSortering) null else sortBy
    }

    fun hentOppgaverForBehandling(referanse: UUID): List<Oppgave> {
        return oppgaveRepository.hentOppgaver(referanse)
    }

    private fun validerOgKombinerFiltre(
        filter: Filter,
        utvidetFilter: UtvidetOppgavelisteFilter?
    ): Filter? {
        if (utvidetFilter == null) return filter
        val avklaringsbehovKoder =
            utledAvklaringsbehovKoderForUtvidetFilter(filter.avklaringsbehovKoder, utvidetFilter.avklaringsbehovKoder)

        if (avklaringsbehovKoder.isEmpty() && utvidetFilter.avklaringsbehovKoder.isNotEmpty() && filter.avklaringsbehovKoder.isNotEmpty()) {
            // det finnes ingen avklaringsbehovkoder som matcher begge filtre. Returnerer null
            return null
        }

        return filter.copy(
            behandlingstyper = utvidetFilter.behandlingstyper,
            avklaringsbehovKoder = avklaringsbehovKoder,
        )
    }

    private fun Oppgave.leggPåMarkeringer(markeringer: List<Markering>): Oppgave =
        this.copy(markeringer = markeringer)

    private fun Oppgave.leggPåUføreVedtak(uførevedtak: UføreVedtak?): Oppgave =
        this.copy(uføreVedtak = uførevedtak)

    private fun List<Oppgave>.filtrerPåTilgang(
        token: OidcToken,
        ident: String
    ): List<Oppgave> {
        val oppgaverFiltrertForKode7 = sjekkTilgangTilFortroligAdresse(enhetService, ident, token, this)
        val enhetsGrupper = enhetService.hentEnheterForIdent(ident, token)
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
        oppgaver: List<Oppgave>
    ): List<Oppgave> =
        if (oppgaver.any { it.harFortroligAdresse == true } && !enhetService.kanSaksbehandleFortroligAdresse(
                ident,
                token
            )) {
            oppgaver.filterNot { it.harFortroligAdresse == true }
        } else {
            oppgaver
        }
}

fun utledAvklaringsbehovKoderForUtvidetFilter(
    filterAvklaringsbehovKoder: Set<String>,
    utvidetFilterAvklaringsbehovKoder: Set<String>
): Set<String> {
    return if (utvidetFilterAvklaringsbehovKoder.isEmpty()) {
        filterAvklaringsbehovKoder
    } else if (filterAvklaringsbehovKoder.isEmpty()) {
        utvidetFilterAvklaringsbehovKoder
    } else {
        utvidetFilterAvklaringsbehovKoder.intersect(filterAvklaringsbehovKoder)
    }
}
