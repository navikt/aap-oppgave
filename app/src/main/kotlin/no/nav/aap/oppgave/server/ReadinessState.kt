package no.nav.aap.oppgave.server

import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

/**
 * Holder applikasjonens tilstand for readiness.
 */
object ReadinessState {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val ready = AtomicBoolean(false)

    fun isReady(): Boolean = ready.get()

    fun setReady() {
        logger.info("Application is ready")
        ready.set(true)
    }

    fun setStopping() {
        logger.info("Application is stopping")
        ready.set(false)
    }
}
