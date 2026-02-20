package no.nav.aap.oppgave.oppdater

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingBehandlingsstatus
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.motor.FlytJobbRepositoryImpl
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphGateway
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.mottattdokument.MottattDokumentRepository
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovHendelse
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovStatus
import no.nav.aap.oppgave.oppdater.hendelse.BehandlingStatus
import no.nav.aap.oppgave.oppdater.hendelse.Endring
import no.nav.aap.oppgave.oppdater.hendelse.OppgaveOppdatering
import no.nav.aap.oppgave.oppdater.hendelse.tilAvklaringsbehovStatus
import no.nav.aap.oppgave.oppdater.hendelse.tilOppgaveOppdatering
import no.nav.aap.oppgave.tilbakekreving.TilbakekrevingRepository
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.postmottak.kontrakt.hendelse.DokumentflytStoppetHendelse
import no.nav.aap.tilgang.AuthorizationBodyPathConfig
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.authorizedPost
import javax.sql.DataSource

fun NormalOpenAPIRoute.oppdaterBehandlingOppgaverApi(
    dataSource: DataSource,
    msGraphClient: IMsGraphGateway,
    prometheus: PrometheusMeterRegistry
) = route("/oppdater-oppgaver").authorizedPost<Unit, Unit, BehandlingFlytStoppetHendelse>(
    routeConfig = AuthorizationBodyPathConfig(
        operasjon = Operasjon.SAKSBEHANDLE,
        applicationsOnly = true,
        applicationRole = "oppdater-behandlingsflyt-oppgaver",
    )
) { _, request ->
    prometheus.httpCallCounter("/oppdater-oppgaver").increment()
    dataSource.transaction { connection ->
        OppdaterOppgaveService(
            msGraphClient,
            oppgaveRepository = OppgaveRepository(connection),
            flytJobbRepository = FlytJobbRepositoryImpl(connection),
            mottattDokumentRepository = MottattDokumentRepository(connection),
            tilbakekrevingRepository = TilbakekrevingRepository(connection),
        ).håndterNyOppgaveOppdatering(
            request.tilOppgaveOppdatering()
        )
    }
    respondWithStatus(HttpStatusCode.OK)
}

fun NormalOpenAPIRoute.oppdaterPostmottakOppgaverApi(
    dataSource: DataSource,
    msGraphClient: IMsGraphGateway,
    prometheus: PrometheusMeterRegistry
) = route("/oppdater-postmottak-oppgaver").authorizedPost<Unit, Unit, DokumentflytStoppetHendelse>(
    routeConfig = AuthorizationBodyPathConfig(
        operasjon = Operasjon.SAKSBEHANDLE,
        applicationsOnly = true,
        applicationRole = "oppdater-postmottak-oppgaver"
    )
) { _, request ->
    prometheus.httpCallCounter("/oppdater-postmottak-oppgaver").increment()
    dataSource.transaction { connection ->
        OppdaterOppgaveService(
            msGraphClient,
            oppgaveRepository = OppgaveRepository(connection),
            flytJobbRepository = FlytJobbRepositoryImpl(connection),
            mottattDokumentRepository = MottattDokumentRepository(connection),
            tilbakekrevingRepository = TilbakekrevingRepository(connection),
        ).håndterNyOppgaveOppdatering(request.tilOppgaveOppdatering())
    }
    respondWithStatus(HttpStatusCode.OK)
}

fun NormalOpenAPIRoute.oppdaterTilbakekrevingOppgaverApi(
    dataSource: DataSource,
    msGraphClient: IMsGraphGateway,
    prometheus: PrometheusMeterRegistry
) = route("/oppdater-tilbakekreving-oppgaver").authorizedPost<Unit, Unit, TilbakekrevingsbehandlingOppdatertHendelse>(
    routeConfig = AuthorizationBodyPathConfig(
        operasjon = Operasjon.SAKSBEHANDLE,
        applicationsOnly = true,
        applicationRole = "oppdater-behandlingsflyt-oppgaver"
    )
) { _, request ->
    prometheus.httpCallCounter("/oppdater-tilbakekreving-oppgaver").increment()
    dataSource.transaction { connection ->
        OppdaterOppgaveService(
            msGraphClient,
            oppgaveRepository = OppgaveRepository(connection),
            flytJobbRepository = FlytJobbRepositoryImpl(connection),
            mottattDokumentRepository = MottattDokumentRepository(connection),
            tilbakekrevingRepository = TilbakekrevingRepository(connection),
        ).håndterNyOppgaveOppdatering(request.tilOppgaveOppdatering())
    }
    respondWithStatus(HttpStatusCode.OK)
}

private fun TilbakekrevingsbehandlingOppdatertHendelse.tilOppgaveOppdatering() =
    OppgaveOppdatering(
        personIdent = this.personIdent,
        saksnummer = this.saksnummer.toString(),
        referanse = this.behandlingref.referanse,
        journalpostId = null,
        behandlingStatus = this.behandlingStatus.tilBehandlingsstatus(),
        behandlingstype = Behandlingstype.TILBAKEKREVING,
        opprettetTidspunkt = this.sakOpprettet,
        avklaringsbehov = this.avklaringsbehov.tilAvklaringsbehovHendelse(),
        vurderingsbehov = emptyList(),
        mottattDokumenter = emptyList(),
        årsakTilOpprettelse = null,
        venteInformasjon = null,
        totaltFeilutbetaltBeløp = this.totaltFeilutbetaltBeløp,
        tilbakekrevingsUrl = this.saksbehandlingURL
    )

private fun List<AvklaringsbehovHendelseDto>.tilAvklaringsbehovHendelse() =
    map {
        AvklaringsbehovHendelse(
            avklaringsbehovKode = AvklaringsbehovKode(kode = it.avklaringsbehovDefinisjon.kode.name),
            status = it.status.tilAvklaringsbehovStatus(),
            endringer = it.endringer.tilEndring(),
        )
    }

private fun List<EndringDTO>.tilEndring() =
    map {
        Endring(
            status = it.status.tilAvklaringsbehovStatus(),
            tidsstempel = it.tidsstempel,
            endretAv = it.endretAv,
            påVentTil = it.frist,
            påVentÅrsak = it.årsakTilSattPåVent?.name,
            begrunnelse = it.begrunnelse,
            årsakTilRetur = it.årsakTilRetur.map { årsakTilRetur -> årsakTilRetur.årsak }
        )
    }

private fun TilbakekrevingBehandlingsstatus.tilBehandlingsstatus() =
    when (this) {
        TilbakekrevingBehandlingsstatus.OPPRETTET -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.TIL_BEHANDLING -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.AVSLUTTET -> BehandlingStatus.LUKKET
        TilbakekrevingBehandlingsstatus.TIL_BESLUTTER -> BehandlingStatus.ÅPEN
        TilbakekrevingBehandlingsstatus.RETUR_FRA_BESLUTTER -> BehandlingStatus.ÅPEN
    }

private fun TilbakekrevingBehandlingsstatus.tilStatus() =
    when (this) {
        TilbakekrevingBehandlingsstatus.OPPRETTET -> AvklaringsbehovStatus.OPPRETTET
        TilbakekrevingBehandlingsstatus.TIL_BEHANDLING -> AvklaringsbehovStatus.OPPRETTET
        TilbakekrevingBehandlingsstatus.AVSLUTTET -> AvklaringsbehovStatus.AVSLUTTET
        TilbakekrevingBehandlingsstatus.TIL_BESLUTTER -> AvklaringsbehovStatus.OPPRETTET
        TilbakekrevingBehandlingsstatus.RETUR_FRA_BESLUTTER -> AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER
    }
