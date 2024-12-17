package no.nav.aap.oppgave.enhet

import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.text.removePrefix
import kotlin.text.startsWith

class EnhetService(private val msGraphClient: IMsGraphClient) {

    suspend fun hentEnheter(currentToken: String, ident: String): List<String> {
        return msGraphClient.hentAdGrupper(currentToken, ident).groups
            .filter { it.name.startsWith(ENHET_GROUP_PREFIX) }
            .map { it.name.removePrefix(ENHET_GROUP_PREFIX) }
    }

    companion object {
        const val ENHET_GROUP_PREFIX = "0000-GA-ENHET_"
    }

}
