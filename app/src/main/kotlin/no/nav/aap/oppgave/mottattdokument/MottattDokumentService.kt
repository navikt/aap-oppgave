package no.nav.aap.oppgave.mottattdokument

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
    fun kvitterLegeerklæring(behandlingRef: UUID, ident: String) {
        log.info("Marker dokumenter for ${behandlingRef} som kvittert")
        mottattDokumentRepository.kvitterDokumenter(behandlingRef, MottattDokumentType.LEGEERKLÆRING, ident)

        val oppgave = oppgaveRepository.hentOppgaver(behandlingRef).firstOrNull { it.status != Status.AVSLUTTET }
        if (oppgave == null) {
            throw RuntimeException("Fant ikke oppgave")
        }

        oppgaveRepository.fjernUkvittertLegeerklæring(OppgaveId(oppgave.id!!, oppgave.versjon))
    }
}