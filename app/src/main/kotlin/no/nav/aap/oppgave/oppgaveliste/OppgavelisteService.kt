package no.nav.aap.oppgave.oppgaveliste

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.miljo.MiljøKode
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.OppgaveRepository.FinnOppgaverDto
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.filter.FilterDto
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.liste.UtvidetOppgavelisteFilter
import no.nav.aap.oppgave.markering.MarkeringDto
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.markering.tilDto
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider

private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService()
const val maksOppgaver = 25

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

    fun hentOppgave(avklaringsbehovReferanseDto: AvklaringsbehovReferanseDto): OppgaveDto? {
        val oppgave = oppgaveRepository.hentOppgave(avklaringsbehovReferanseDto)
        if (avklaringsbehovReferanseDto.referanse != null) {
            val markeringer = markeringRepository.hentMarkeringerForBehandling(avklaringsbehovReferanseDto.referanse!!)
            return oppgave?.leggPåMarkeringer(markeringer.tilDto())
        }
        return oppgave
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
        ident: String
    ): FinnOppgaverDto {
        val rekkefølge =
            when (Miljø.er()) {
                MiljøKode.DEV -> OppgaveRepository.Rekkefølge.desc
                else -> OppgaveRepository.Rekkefølge.asc
            }

        val finnOppgaverDto =
            if (unleashService.isEnabled(FeatureToggles.UtvidetOppgaveFilter)) {
                val kombinertFilter = settFilter(filter, utvidetFilter)
                oppgaveRepository.finnOppgaver(
                    filter =
                        kombinertFilter.copy(
                            enheter = enheter,
                            veileder = veilederIdent
                        ),
                    rekkefølge = rekkefølge,
                    paging = paging,
                    kunLedigeOppgaver = kunLedigeOppgaver
                )
            } else {
                oppgaveRepository.finnOppgaver(
                    filter = filter.copy(enheter = enheter, veileder = veilederIdent),
                    rekkefølge = rekkefølge,
                    paging = paging,
                    kunLedigeOppgaver = kunLedigeOppgaver
                )
            }
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
            antallGjenstaaende = finnOppgaverDto.antallGjenstaaende
        )
    }

    fun hentMineOppgaver(
        ident: String,
        token: OidcToken,
        kunPaaVent: Boolean?
    ): List<OppgaveDto> =
        oppgaveRepository.hentMineOppgaver(ident = ident, kunPåVent = kunPaaVent == true).map {
            it.leggPåMarkeringer(
                markeringRepository.hentMarkeringerForBehandling(requireNotNull(it.behandlingRef) {
                    "Fant ikke behandlingsreferanse for oppgave med id ${it.id}"
                }).tilDto()
            )
        }.medPersonNavn(fjernSensitivInformasjonNårTilgangMangler = false, token = token)

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
        val oppgaverFiltrertForKode6 = sjekkTilgangTilFortroligAdresse(enhetService, token.token(), this)
        val enhetsGrupper = enhetService.hentEnheter(token.token(), ident)
        return oppgaverFiltrertForKode6
            .asSequence()
            .filter { enhetsGrupper.contains(it.enhetForKø()) }
            .take(maksOppgaver)
            .toList()
    }

    private fun sjekkTilgangTilFortroligAdresse(
        enhetService: EnhetService,
        token: String,
        oppgaver: List<OppgaveDto>
    ): List<OppgaveDto> =
        if (!enhetService.kanSaksbehandleFortroligAdresse(token)) {
            oppgaver.filterNot { it.harFortroligAdresse == true }
        } else {
            oppgaver
        }
}
