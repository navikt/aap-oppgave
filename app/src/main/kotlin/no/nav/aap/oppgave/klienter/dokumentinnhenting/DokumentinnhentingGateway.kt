package no.nav.aap.oppgave.klienter.dokumentinnhenting

import no.nav.aap.oppgave.påminnelse.PåminnelseInfo
import java.util.UUID

interface DokumentinnhentingGateway {
    fun hentPåminnelserForBehandling(behandlingsreferanse: UUID): List<PåminnelseInfo>
}