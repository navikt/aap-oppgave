package no.nav.aap.oppgave

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metriker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import no.nav.aap.oppgave.verdityper.OppgaveId
import javax.sql.DataSource

fun NormalOpenAPIRoute.mineOppgaverApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/mine-oppgaver").get<Unit, List<OppgaveDto>> {
        prometheus.httpCallCounter("/mine-oppgaver").increment()
        val mineOppgaver = dataSource.transaction(readOnly = true) { connection ->
            OppgaveRepository(connection).hentMineOppgaver(ident())
        }
        respond(mineOppgaver)
    }

fun NormalOpenAPIRoute.avsluttOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/avslutt-oppgave").post<Unit, List<OppgaveId>, OppgaveReferanseDto> { _, dto ->
        prometheus.httpCallCounter("/avslutt-oppgave").increment()
        val oppgaver = dataSource.transaction { connection ->
            val innloggetBrukerIdent = ident()
            val oppgaverSomSkalAvsluttes = OppgaveRepository(connection).hentOppgaverForReferanse(
                dto.saksnummer,
                dto.referanse,
                dto.journalpostId,
                dto.avklaringsbehovtype,
                innloggetBrukerIdent
            )
            oppgaverSomSkalAvsluttes.forEach {
                OppgaveRepository(connection).avsluttOppgave(it,innloggetBrukerIdent)
            }
            oppgaverSomSkalAvsluttes
        }
        respond(oppgaver)
    }

fun NormalOpenAPIRoute.avreserverOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/avreserver-oppgave").post<Unit, List<OppgaveId>, OppgaveReferanseDto> { _, dto ->
        prometheus.httpCallCounter("avreserver-oppgave").increment()
        val oppgaver = dataSource.transaction { connection ->
            val oppgaverSomSkalAvreserveres = OppgaveRepository(connection).hentOppgaverForReferanse(
                dto.saksnummer,
                dto.referanse,
                dto.journalpostId,
                dto.avklaringsbehovtype,
                ident()
            )
            oppgaverSomSkalAvreserveres.forEach {
                OppgaveRepository(connection).avreserverOppgave(it, ident())
            }
            oppgaverSomSkalAvreserveres
        }
        respond(oppgaver)
    }


fun NormalOpenAPIRoute.flyttOppgave(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/flytt-oppgave").post<Unit, List<OppgaveId>, FlyttOppgaveDto> { _, dto ->
        prometheus.httpCallCounter("flytt-oppgave").increment()

        val oppgaver = dataSource.transaction { connection ->
            val innloggetBrukerIdent = ident()
            val oppgaverSomSkalFlyttes = OppgaveRepository(connection).hentOppgaverForReferanse(
                dto.oppgaveReferanseDto.saksnummer,
                dto.oppgaveReferanseDto.referanse,
                dto.oppgaveReferanseDto.journalpostId,
                dto.oppgaveReferanseDto.avklaringsbehovtype,
                innloggetBrukerIdent
            )
            oppgaverSomSkalFlyttes.forEach {
                OppgaveRepository(connection).reserverOppgave(it, innloggetBrukerIdent, dto.flyttTilIdent)
            }
        }

    }