package no.nav.aap.oppgave.tilgang

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.tilgang.AdGruppe

object SaksbehandlerNasjonal : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_SAKSBEHANDLER_NASJONAL")
}
object SaksbehandlerOppfolging : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_SAKSBEHANDLER_OPPFOLGING")
}
object Kvalitetssikrer : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_KVALITETSSIKRER")
}
object Beslutter : AdGruppe {
    override val id: String = requiredConfigForKey("AAP_BESLUTTER")
}