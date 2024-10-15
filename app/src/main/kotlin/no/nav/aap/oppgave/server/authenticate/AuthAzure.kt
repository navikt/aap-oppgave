package no.nav.aap.oppgave.server.authenticate

import com.papsign.ktor.openapigen.route.response.OpenAPIPipelineContext
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

internal fun OpenAPIPipelineContext.ident(): String {
    return requireNotNull(pipeline.call.principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getClaim("NAVident", String::class)
        ?: error("Ident mangler i token claims")
}