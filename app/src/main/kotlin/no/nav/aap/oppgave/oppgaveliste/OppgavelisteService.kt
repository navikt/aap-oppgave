package no.nav.aap.oppgave.oppgaveliste

import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.miljo.MiljøKode
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.HarMottattDokument
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.OppgaveRepository.FinnOppgaverDto
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.enhet.OppgaveEnhetDto
import no.nav.aap.oppgave.filter.Filter
import no.nav.aap.oppgave.beslutter.IkkeBeslutterRepository
import no.nav.aap.oppgave.gjenståendesykedager.GjenståendeSykedagerRepository
import no.nav.aap.oppgave.liste.OppgaveSorteringFelt
import no.nav.aap.oppgave.liste.OppgaveSorteringRekkefølge
import no.nav.aap.oppgave.liste.Paging
import no.nav.aap.oppgave.liste.UtvidetOppgavelisteFilter
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import no.nav.aap.oppgave.uføreVedtak.UføreVedtakRepository
import no.nav.aap.oppgave.verdityper.Behandlingstype
import java.util.UUID

const val maksOppgaver = 50

class OppgavelisteService(
    private val oppgaveRepository: OppgaveRepository,
    private val markeringRepository: MarkeringRepository,
    private val uføreVedtakRepository: UføreVedtakRepository,
    private val gjenståendeSykedagerRepository: GjenståendeSykedagerRepository,
    private val ikkeBeslutterRepository: IkkeBeslutterRepository,
    private val mottattDokumentRepository: MottattDokumentRepository,
    private val enhetService: EnhetService,
) {
    fun søkEtterOppgaver(søketekst: String): List<Oppgave> {
        val oppgaver = if (søketekst.all { it.isDigit() }) {
            oppgaveRepository.finnÅpneOppgaverGittPersonident(søketekst)
        } else {
            oppgaveRepository.finnOppgaverGittSaksnummer(søketekst)
        }

        return oppgaver.berikOppgaver()
    }

    fun hentAktivOppgave(behandlingReferanse: BehandlingReferanse): Oppgave? {
        val oppgave = oppgaveRepository.hentAktivOppgave(behandlingReferanse)
        if (oppgave != null && oppgave.erÅpen) {
            return listOf(oppgave).berikOppgaver().single()
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

        val finnOppgaverDto = oppgaveRepository.finnOppgaver(
            filter = kombinertFilter.copy(
                enheter = enheter,
                veileder = veilederIdent
            ),
            rekkefølge = sortOrderMedDefault,
            paging = paging,
            kunLedigeOppgaver = kunLedigeOppgaver,
            utvidetFilter = utvidetFilter,
            sortBy = sortBy,
            enheterMedNavn = enhetService.hentEnheterMedNavn().takeIf { filter.navn == "Kvalitetssikrer" }.orEmpty(),
            hastemarkeringerFørst = hastemarkeringerFørst
        )

        val oppgaver = finnOppgaverDto.oppgaver
            .berikOppgaver(forOppgaveliste = true)

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

        val oppgaver = oppgaveRepository.hentMineOppgaver(
            ident = ident,
            kunPåVent = kunPaaVent == true,
            sortBy = sortBy,
            sortOrder = sortOrder
        ).berikOppgaver(forOppgaveliste = true).hentPersonNavn()

        val (medMarkering, utenMarkering) = oppgaver.partition { it.markeringer.isNotEmpty() }
        return medMarkering + utenMarkering
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

    private fun List<Oppgave>.berikOppgaver(forOppgaveliste: Boolean = false): List<Oppgave> {
        return when (forOppgaveliste) {
            true -> this
                .leggPåMarkeringer()
                .leggPåUføreVedtak()
                .leggPåMottattDokument()
                .leggPåGjenståendeSykedager()
                .leggPåIkkeBesluttere()
            false -> this
                .leggPåMarkeringer()
                .leggPåUføreVedtak()
                .leggPåMottattDokument()
        }
    }

    private fun List<Oppgave>.leggPåMarkeringer(): List<Oppgave> {
        if (isEmpty()) {
            return this
        }
        val markeringerPerBehandling =
            markeringRepository.hentGjeldendeMarkeringerForBehandlinger(map { it.behandlingRef })
        return map { oppgave ->
            oppgave.copy(markeringer = markeringerPerBehandling[oppgave.behandlingRef].orEmpty())
        }
    }

    private fun List<Oppgave>.leggPåUføreVedtak(): List<Oppgave> {
        val behandlingstyperMedUføreVedtak =
            setOf(Behandlingstype.FØRSTEGANGSBEHANDLING, Behandlingstype.REVURDERING)

        val behandlingRefs = filter { it.behandlingstype in behandlingstyperMedUføreVedtak }.map { it.behandlingRef }
        if (behandlingRefs.isEmpty()) {
            return this
        }
        val uføreVedtakPerBehandling = uføreVedtakRepository.hentAktiveUføreVedtakForBehandlinger(behandlingRefs)
        return map { oppgave ->
            if (oppgave.behandlingstype in behandlingstyperMedUføreVedtak) {
                oppgave.copy(uføreVedtak = uføreVedtakPerBehandling[oppgave.behandlingRef])
            } else {
                oppgave
            }
        }
    }

    private fun List<Oppgave>.leggPåMottattDokument(): List<Oppgave> {
        if (isEmpty()) {
            return this
        }
        val typePerBehandling =
            mottattDokumentRepository.hentSisteUlesteDokumentTypePerBehandling(map { it.behandlingRef })
        return map { oppgave ->
            oppgave.copy(
                harMottattDokument = typePerBehandling[oppgave.behandlingRef]
                    ?.let { HarMottattDokument(dokumentType = it) }
            )
        }
    }

    private fun Oppgave.leggPåGjenståendeSykedager(): Oppgave =
        listOf(this).leggPåGjenståendeSykedager().single()

    private fun List<Oppgave>.leggPåGjenståendeSykedager(): List<Oppgave> {
        if (isEmpty()) {
            return this
        }
        val sykedagerPerBehandling = gjenståendeSykedagerRepository.hentForBehandlinger(map { it.behandlingRef })
        return map { oppgave ->
            oppgave.copy(gjenståendeSykedager = sykedagerPerBehandling[oppgave.behandlingRef])
        }
    }

    private fun List<Oppgave>.leggPåIkkeBesluttere(): List<Oppgave> {
        if (isEmpty()) {
            return this
        }
        val ikkeBesluttere = ikkeBeslutterRepository.hentForBehandlinger(map { it.behandlingRef })
        return map { oppgave ->
            oppgave.copy(saksbehandlereSomIkkeKanBeslutte = ikkeBesluttere[oppgave.behandlingRef])
        }
    }

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
