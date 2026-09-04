package no.nav.aap.oppgave.uføreVedtak

import no.nav.aap.oppgave.mottattdokument.MottattDokumentService
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger(MottattDokumentService::class.java)


class UføreVedtakService (
    private val uføreVedtakRepository: UføreVedtakRepository,
) {
    fun fjernUføreVedtakPåBehandling(behandlingRef: UUID, ident: String) {
        uføreVedtakRepository.fjernUføreVedtakTag(behandlingRef, ident)
        log.info("$ident fjernet uførevedtak for behandling $behandlingRef")
    }
}