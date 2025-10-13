package no.nav.aap.oppgave.metrikker

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun PrometheusMeterRegistry.httpCallCounter(path: String): Counter = this.counter(
    "http_call",
    listOf(Tag.of("path", path))
)

fun MeterRegistry.cacheHit(service: CachedService): Counter =
    this.counter("cache_hit", listOf(service.tag()))

fun MeterRegistry.cacheMiss(service: CachedService): Counter =
    this.counter("cache_miss", listOf(service.tag()))


interface MetricKey

enum class CachedService : MetricKey {
    NOM_ANSATT,
    NOM_EGENANSATT,
    MSGRAPH_ENHETSGRUPPER,
    MSGRAPH_FORTROLIG_ADRESSE,
    MSGRAPH_MEDLEMMER_I_GRUPPE,
    SYFO_VEILEDER,
    VEILARBOPP_VEILEDER,
    VEILARBARENA_ENHET,
    ;

    fun tag(): Tag = Tag.of("service", this.name.lowercase())
}
