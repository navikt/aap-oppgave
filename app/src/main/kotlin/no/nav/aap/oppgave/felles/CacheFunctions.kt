package no.nav.aap.oppgave.felles

import com.github.benmanes.caffeine.cache.Cache
import no.nav.aap.oppgave.metrikker.CachedService
import no.nav.aap.oppgave.metrikker.cacheHit
import no.nav.aap.oppgave.metrikker.cacheMiss
import no.nav.aap.oppgave.metrikker.prometheus

/**
 * TODO:
 *  Gå over til å bruke Cache.get() for å unngå race condtions.
 *  Bruker getIfPresent() midlertidig for å kunne telle treff/bom i cachen og se verdien av cachen.
 *  Om vi bruker .get() er det kun mulig å måle antall bom, ikke treff, som gir mindre mening.
 **/
fun <K : Any, V> withCache(
    cache: Cache<K, V>,
    cacheKey: K,
    cacheService: CachedService,
    block: () -> V
): V {
    val cachedValue = cache.getIfPresent(cacheKey)
    if (cachedValue != null) {
        prometheus.cacheHit(cacheService).increment()
        return cachedValue
    }

    prometheus.cacheMiss(cacheService).increment()

    return block().also {
        if (it != null) {
            cache.put(cacheKey, it)
        }
    }
}
