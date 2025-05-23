package no.nav.aap.oppgave.metrikker

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun PrometheusMeterRegistry.httpCallCounter(path: String): Counter = this.counter(
    "http_call",
    listOf(Tag.of("path", path))
)