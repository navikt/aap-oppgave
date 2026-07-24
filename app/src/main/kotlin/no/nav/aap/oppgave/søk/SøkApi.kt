package no.nav.aap.oppgave.søk

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.bruker
import no.nav.aap.komponenter.server.auth.token
import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.SakOgAvklaringsbehov
import no.nav.aap.oppgave.enhet.Enhet
import no.nav.aap.oppgave.enhet.EnhetService
import no.nav.aap.oppgave.klienter.norg.INorgGateway
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteService
import no.nav.aap.oppgave.oppgaveliste.OppgavelisteUtils.hentPersonNavn
import no.nav.aap.oppgave.plukk.TilgangService
import no.nav.aap.tilgang.Operasjon
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Fritekstsøk etter oppgave. Hvis søketekster er 11 tegn så søkes det etter oppgaver knyttet til fødselsnummer.
 * Ellers søkers det etter oppgave knyttet til saksnummer.
 */
private val log = LoggerFactory.getLogger("søkApi")
fun NormalOpenAPIRoute.søkApi(
    dataSource: DataSource,
    enhetService: EnhetService,
    norgGateway: INorgGateway,
    prometheus: PrometheusMeterRegistry
) {
    route("/sok").post<Unit, SøkResponse, SøkRequest> { _, søk ->
        prometheus.httpCallCounter("/sok").increment()
        val oppgaver =
            dataSource.transaction(readOnly = true) { connection ->
                val søketekst = søk.søketekst.trim()
                OppgavelisteService(
                    OppgaveRepository(connection),
                    MarkeringRepository(connection),
                    enhetService,
                    norgGateway
                ).søkEtterOppgaver(søketekst)
            }

        if (oppgaver.isEmpty()) {
            log.info("Fant ingen oppgaver basert på søketeksten")
        }

        val harAdressebeskyttelse = oppgaver.any { harAdressebeskyttelse(it) }
        val harTilgang = oppgaver.all {
            TilgangService.sjekkTilgang(
                it.tilAvklaringsbehovReferanseDto(),
                token(),
                Operasjon.SE
            )
        }

        respond(
            SøkResponse(
                oppgaver = oppgaver.hentPersonNavn().map { it.tilOppgaveISøkResponse() },
                harTilgang = harTilgang,
                harAdressebeskyttelse = harAdressebeskyttelse,
            )
        )
    }
}

fun NormalOpenAPIRoute.sistEndretApi(
    dataSource: DataSource,
) {
    route("/mine-siste-oppgaver").get<Unit, List<SakOgAvklaringsbehov>> {
        val resultat = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection)
                .hentSisteEndretAvSaksbehandler(bruker())
        }

        respond(resultat)
    }
}

private fun harAdressebeskyttelse(oppgave: Oppgave): Boolean =
    oppgave.enhet == Enhet.NAV_VIKAFOSSEN.kode ||
            erEgenAnsattEnhet(oppgave) ||
            oppgave.harFortroligAdresse == true

private fun erEgenAnsattEnhet(oppgave: Oppgave): Boolean {
    // alle kontorer som slutter på 83 er egen ansatt-kontor, unntatt Nav Værnes
    return oppgave.enhet.endsWith("83") && oppgave.enhet != Enhet.NAV_VÆRNES.kode
}

private fun Oppgave.hentPersonNavn() = listOf(this).hentPersonNavn().first()

private fun Oppgave.tilOppgaveISøkResponse(): OppgaveISøkResponse {
    return OppgaveISøkResponse(
        personNavn = this.hentPersonNavn().personNavn,
        reservertAvIdent = reservertAv,
        erPåVent = this.påVentTil != null,
        typeMarkeringer = this.markeringer.map { it.markeringType },
        enhetForKø = this.enhetForKø,
        avklaringsbehovKode = this.avklaringsbehovKode,
        behandlingskontekst = BehandlingskontekstResponse(
            behandlingstype = this.behandlingstype,
            behandlingsreferanse = this.behandlingRef,
            saksnummer = this.saksnummer,
            journalpostId = this.journalpostId,
            tilbakekrevingUrl = this.tilbakekrevingsVars?.tilbakekrevings_URL
        ),
    )
}