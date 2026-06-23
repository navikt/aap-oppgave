package no.nav.aap.oppgave.markering

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.response.respondWithStatus
import com.papsign.ktor.openapigen.route.route
import io.ktor.http.HttpStatusCode
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.server.auth.bruker
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.prosessering.sendOppgaveStatusOppdatering
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.verdityper.Status
import org.slf4j.LoggerFactory
import javax.sql.DataSource
import no.nav.aap.oppgave.klienter.nom.ansattinfo.AnsattInfoGateway
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger("markeringApi")

fun NormalOpenAPIRoute.markeringApi(
    dataSource: DataSource,
    prometheus: PrometheusMeterRegistry,
    ansattInfoGateway: AnsattInfoGateway,
) {
    // Deprecated: /ny-hendelse skal ta over for denne
    route("/{referanse}/ny-markering").post<BehandlingReferanse, BehandlingReferanse, OpprettMarkeringDto> { request, dto ->
        dataSource.transaction { connection ->
            MarkeringRepository(connection).oppdaterMarkering(
                referanse = request.referanse,
                BehandlingMarkering(
                    markeringType = dto.markeringType,
                    begrunnelse = dto.begrunnelse,
                    opprettetAv = bruker().ident,
                    opprettetAvNavn = ansattInfoGateway.hentAnsattNavnHvisFinnes(bruker().ident),
                    opprettetTidspunkt = LocalDateTime.now(),
                )
            )
            val oppgavePåBehandling =
                OppgaveRepository(connection).hentOppgaver(request.referanse).first { it.status == Status.OPPRETTET }

            if (oppgavePåBehandling.id != null) {
                log.info("Sender oppdatering til statistikk pga. ny markering på behandling. OppgaveId: ${oppgavePåBehandling.id}, behandlingsreferanse: ${oppgavePåBehandling.behandlingRef}")
                sendOppgaveStatusOppdatering(
                    oppgaveId = oppgavePåBehandling.oppgaveId(),
                    hendelseType = HendelseType.OPPDATERT,
                    repository = FlytJobbRepository(connection),
                )
            }
        }

        respondWithStatus(HttpStatusCode.OK)
    }

    route("/{referanse}/ny-hendelse").post<BehandlingReferanse, BehandlingReferanse, OpprettMarkeringDto> { request, dto ->
        dataSource.transaction { connection ->
            val oppgavePåBehandling =
                OppgaveRepository(connection).hentOppgaver(request.referanse).firstOrNull { it.status == Status.OPPRETTET }

            if (oppgavePåBehandling?.id != null) {
                log.info("Sender oppdatering til statistikk pga. ny markering på behandling. OppgaveId: ${oppgavePåBehandling.id}, behandlingsreferanse: ${oppgavePåBehandling.behandlingRef}")
                sendOppgaveStatusOppdatering(
                    oppgaveId = oppgavePåBehandling.oppgaveId(),
                    hendelseType = HendelseType.OPPDATERT,
                    repository = FlytJobbRepository(connection),
                )
            }

            MarkeringRepository(connection).lagreMarkeringNy(
                referanse = request.referanse,
                BehandlingMarkering(
                    markeringType = dto.markeringType,
                    begrunnelse = dto.begrunnelse,
                    opprettetAv = bruker().ident,
                    opprettetAvNavn = ansattInfoGateway.hentAnsattNavnHvisFinnes(bruker().ident),
                    hendelseType = dto.hendelseType,
                    opprettetTidspunkt = LocalDateTime.now(),
                )
            )
        }

        respondWithStatus(HttpStatusCode.OK)
    }

    // Deprecated: /ny-hendelse skal ta over for denne
    route("/{referanse}/hent-markeringer").get<BehandlingReferanse, List<MarkeringDto>> { request ->
        prometheus.httpCallCounter("/hent-markeringer").increment()

        val markeringer =
            dataSource.transaction { connection ->
                MarkeringRepository(connection).hentMarkeringerOgHistorikk(request.referanse)
            }
        respond(markeringer.tilDto())
    }

    route("/{referanse}/hent-markeringer-og-historikk").get<BehandlingReferanse, List<MarkeringDto>> { request ->
        prometheus.httpCallCounter("/hent-markeringer-og-historikk").increment()

        val markeringer =
            dataSource.transaction { connection ->
                MarkeringRepository(connection).hentMarkeringerOgHistorikk(request.referanse)
            }
        respond(markeringer.tilDto())
    }

    route("/{referanse}/hent-siste-aktive-hastemarkering").get<BehandlingReferanse, List<MarkeringDto>> { request ->
        prometheus.httpCallCounter("/hent-siste-aktive-hastemarkering").increment()

        val markeringer =
            dataSource.transaction { connection ->
                MarkeringRepository(connection).hentSisteAktiveMarkering(request.referanse, MarkeringForBehandling.HASTER)
            }
        respond(markeringer.tilDto())
    }

    // Deprecated: /ny-hendelse skal ta over for denne
    route("/{referanse}/fjern-markering").post<BehandlingReferanse, BehandlingReferanse, MarkeringDto> { request, dto ->
        prometheus.httpCallCounter("/fjern-markering").increment()
        dataSource.transaction { connection ->
            MarkeringRepository(connection).slettMarkering(
                request.referanse,
                BehandlingMarkering(
                    markeringType = dto.markeringType,
                    begrunnelse = dto.begrunnelse,
                    opprettetAv = bruker().ident,
                    opprettetTidspunkt = LocalDateTime.now(),
                )
            )

            val oppgavePåBehandling =
                OppgaveRepository(connection).hentOppgaver(request.referanse).first { it.status == Status.OPPRETTET }

            if (oppgavePåBehandling.id != null) {
                log.info("Sender oppdatering til statistikk pga. fjernet markering på behandling. OppgaveId: ${oppgavePåBehandling.id}, behandlingsreferanse: ${oppgavePåBehandling.behandlingRef}")
                sendOppgaveStatusOppdatering(
                    oppgaveId = oppgavePåBehandling.oppgaveId(),
                    hendelseType = HendelseType.OPPDATERT,
                    repository = FlytJobbRepository(connection),
                )
            }
        }
        respondWithStatus(HttpStatusCode.OK)
    }
}

fun List<BehandlingMarkering>.tilDto(): List<MarkeringDto> {
    return map {
        MarkeringDto(
            markeringType = it.markeringType,
            begrunnelse = it.begrunnelse,
            opprettetAv = it.opprettetAv,
            opprettetAvNavn = it.opprettetAvNavn,
            opprettetTidspunkt = it.opprettetTidspunkt,
            hendelseType = it.hendelseType,
        )
    }
}