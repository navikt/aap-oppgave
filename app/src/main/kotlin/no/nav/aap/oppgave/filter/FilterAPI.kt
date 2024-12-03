package no.nav.aap.oppgave.filter

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.oppgave.metrikker.httpCallCounter
import no.nav.aap.oppgave.server.authenticate.ident
import java.time.LocalDateTime
import javax.sql.DataSource

fun NormalOpenAPIRoute.hentFilterApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/filter").get<Unit, List<FilterDto>> {
        prometheus.httpCallCounter("/filter").increment()
        val filterListe = dataSource.transaction(readOnly = true) { connection ->
            FilterRepository(connection).hentAlle()
        }
        respond(filterListe)
    }

fun NormalOpenAPIRoute.opprettEllerOppdaterFilterApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/filter").post<Unit, FilterDto, FilterDto> { _, request ->
        prometheus.httpCallCounter("/filter").increment()

        val filterId = dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)

            if (request.id != null) {
                filterRepo.oppdater(OppdaterFilter(
                    id = request.id!!,
                    navn = request.navn,
                    beskrivelse = request.beskrivelse,
                    avklaringsbehovtyper = request.avklaringsbehovKoder,
                    behandlingstyper = request.behandlingstyper,
                    endretAv = ident(),
                    endretTidspunkt = LocalDateTime.now(),
                ))
            } else {
                filterRepo.opprett(OpprettFilter(
                    navn = request.navn,
                    beskrivelse = request.beskrivelse,
                    avklaringsbehovtyper = request.avklaringsbehovKoder,
                    behandlingstyper = request.behandlingstyper,
                    opprettetAv = ident(),
                    opprettetTidspunkt = LocalDateTime.now(),
                ))
            }
        }
        respond(request.copy(id = filterId))
    }

fun NormalOpenAPIRoute.slettFilterApi(dataSource: DataSource, prometheus: PrometheusMeterRegistry) =

    route("/filter/{filterId}/slett").post<Unit, Unit, FilterId> { _, req ->
        prometheus.httpCallCounter("/filter/{filterId}/slett").increment()
        dataSource.transaction { connection ->
            FilterRepository(connection).slettFilter(req.filterId)
        }
        respond(Unit)
    }
