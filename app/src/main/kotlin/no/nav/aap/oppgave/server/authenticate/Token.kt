package no.nav.aap.oppgave.server.authenticate

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.collections.set
import kotlin.let

internal data class Token(val expires_in: Long, val access_token: String) {
    private val expiry: Instant = Instant.now().plusSeconds(expires_in - LEEWAY_SECONDS)

    internal fun expired() = Instant.now().isAfter(expiry)

    private companion object {
        const val LEEWAY_SECONDS = 60
    }

    override fun toString(): String {
        return "($expires_in, $access_token)"
    }
}

internal class TokenCache {
    private val tokens: HashMap<String, Token> = hashMapOf()
    private val mutex = Mutex()

    internal suspend fun add(key: String, token: Token) {
        mutex.withLock {
            tokens[key] = token
        }
    }

    internal suspend fun get(key: String): Token? {
        mutex.withLock {
            tokens[key]
        }?.let {
            if (it.expired()) {
                rm(key)
            }
        }

        return mutex.withLock {
            tokens[key]
        }
    }

    private suspend fun rm(key: String) {
        mutex.withLock {
            tokens.remove(key)
        }
    }
}
