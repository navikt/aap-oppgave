package no.nav.aap.oppgave.oppgaveliste

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlGateway
import no.nav.aap.oppgave.metrikker.prometheus
import java.time.Duration

object OppgavelisteUtils {
    private val personinfoCache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(Duration.ofHours(6))
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

    private fun hentNavnFraPDL(identer: List<String>): Map<String, String> {
        return PdlGraphqlGateway
            .withClientCredentialsRestClient()
            .hentPersoninfoForIdenter(identer)
            .hentPersonBolk
            ?.associate {
                val navn = it.person?.navn?.firstOrNull()?.fulltNavn() ?: ""
                personinfoCache.put(it.ident, navn)

                it.ident to navn
            }
            ?: emptyMap()
    }

    private fun OppgaveDto.medNavn(
        ident: String?,
        samletNavnMap: Map<String, String?>
    ): OppgaveDto {
        val personNavn = ident?.let { samletNavnMap[it] } ?: ""

        return this.copy(personNavn = personNavn)
    }
}
