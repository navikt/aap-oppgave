package no.nav.aap.oppgave.mottattdokument

import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.verdityper.Status
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger(OppgaveRepository::class.java)

class MottattDokumentService(
    val mottattDokumentRepository: MottattDokumentRepository,
    val oppgaveRepository: OppgaveRepository,
) {
    fun registrerDokumenterLest(behandlingRef: UUID, ident: String) {
        log.info("Registrerer dokumenter for ${behandlingRef} som lest")
        val ulesteDokumenter = mottattDokumentRepository.hentUlesteDokumenter(behandlingRef)

        if (ulesteDokumenter.isEmpty()) {
            log.warn("Fant ingen dokumenter som ikke er registrert som lest for behanding $behandlingRef")
            return
        }

        mottattDokumentRepository.registrerDokumenterSomLest(behandlingRef, ident)
        log.info("Registrerte ${ulesteDokumenter.size} dokument(er) av type ${ulesteDokumenter.joinToString(", ") { it.type }} som lest")

        val oppgave = oppgaveRepository.hentOppgaver(behandlingRef).firstOrNull { it.status != Status.AVSLUTTET }
        require(oppgave != null) { "Fant ingen åpen oppgave for behandling $behandlingRef" }

        oppgaveRepository.settUlesteDokumenter(
            oppgaveId = oppgave.oppgaveId(),
            harUlesteDokumenter = false
        )
    }

    private fun OppgaveDto.oppgaveId() = OppgaveId(id!!, versjon)
}