package no.nav.aap.oppgave.oppgaveliste

import com.github.benmanes.caffeine.cache.Caffeine
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlGateway
import no.nav.aap.oppgave.metrikker.prometheus
import org.flywaydb.core.api.logging.LogFactory
import java.time.Duration
import kotlin.time.measureTimedValue

object OppgavelisteUtils {
    private val logger = LogFactory.getLog(OppgavelisteUtils::class.java)

    private val personinfoCache = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(Duration.ofHours(12))
        .recordStats()
        .build<String, String>() // <Ident, Navn>

    init {
        CaffeineCacheMetrics.monitor(prometheus, personinfoCache, "oppgave_navn")
    }

    fun List<OppgaveDto>.hentPersonNavn(): List<OppgaveDto> {
        val identer = mapNotNull { it.personIdent }.distinct()
        if (identer.isEmpty()) {
            return this
        }

        val (identerMangler, identerICache) = identer.partition { personinfoCache.getIfPresent(it) == null }

        val navnMapFraCache = identerICache
            .associateWith { personinfoCache.getIfPresent(it) }
            .filterNot { it.value.isNullOrBlank() }

        if (identerMangler.isEmpty()) {
            return this.map { it.medNavn(it.personIdent, navnMapFraCache) }
        }

        val navnMap = hentNavnFraPDL(identerMangler) + navnMapFraCache

        return this.map { it.medNavn(it.personIdent, navnMap) }
    }

    private fun hentNavnFraPDL(identer: List<String>): Map<String, String> = measureTimedValue {
        runCatching {
            identer.chunked(1000)
                .flatMap { bolk ->
                    hentNavnForBolk(bolk)
                }.toMap()
        }
    }.let { (result, duration) ->
        if (result.isSuccess) logger.info("Hentet navn for ${identer.size} identer fra PDL (tid: $duration)")
        else logger.error("Feil ved henting av ${identer.size} identer fra PDL (tid: $duration)")

        result.getOrThrow()
    }

    private fun hentNavnForBolk(identer: List<String>): List<Pair<String, String>> {
        return PdlGraphqlGateway
            .withClientCredentialsRestClient()
            .hentPersoninfoForIdenter(identer)
            .hentPersonBolk
            ?.map {
                val navn = it.person?.navn?.firstOrNull()?.fulltNavn() ?: ""
                personinfoCache.put(it.ident, navn)
                it.ident to navn
            }
            ?: emptyList()
    }

    private fun OppgaveDto.medNavn(
        ident: String?,
        samletNavnMap: Map<String, String?>
    ): OppgaveDto {
        val personNavn = ident?.let { samletNavnMap[it] } ?: ""

        return this.copy(personNavn = personNavn)
    }
}

