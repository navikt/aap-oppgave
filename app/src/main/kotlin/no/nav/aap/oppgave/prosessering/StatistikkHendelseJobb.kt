package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.markering.BehandlingMarkering
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import no.nav.aap.oppgave.statistikk.OppgaveTilStatistikkDto
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private val logger = LoggerFactory.getLogger("prosessering.StatistikkHendelseJobb")

class StatistikkHendelseJobb(
    private val oppgaveRepository: OppgaveRepository,
    private val markeringRepository: MarkeringRepository
) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelsesType = HendelseType.valueOf(input.parameter("hendelsesType"))
        val oppgaveId = DefaultJsonMapper.fromJson<OppgaveId>(input.payload())

        oppgaveRepository.hentOppgave(oppgaveId.id).let { oppgaveDto ->
            val markeringer = markeringRepository.hentGjeldendeMarkeringerForBehandling(oppgaveDto.behandlingRef)
            StatistikkGateway.avgiHendelse(
                OppgaveHendelse(
                    hendelse = hendelsesType,
                    oppgaveTilStatistikkDto = fraOppgave(oppgaveDto, markeringer),
                    sendtTidspunkt = LocalDateTime.now()
                )
            )
        }
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return StatistikkHendelseJobb(OppgaveRepository(connection), MarkeringRepository(connection))
        }

        override fun type(): String {
            return "hendelse.statistikk"
        }

        override fun navn(): String {
            return "Send oppgave-endringer til statistikk-appen."
        }

        override fun beskrivelse(): String {
            return "Send oppgave-endringer til statistikk-appen."
        }
    }
}

private fun fraOppgave(oppgave: Oppgave, markeringer: List<BehandlingMarkering>): OppgaveTilStatistikkDto {
    return OppgaveTilStatistikkDto(
        id = oppgave.id,
        personIdent = oppgave.personIdent,
        saksnummer = oppgave.saksnummer,
        behandlingRef = oppgave.behandlingRef,
        journalpostId = oppgave.journalpostId,
        enhet = oppgave.enhetForKø,
        avklaringsbehovKode = oppgave.avklaringsbehovKode,
        status = oppgave.status,
        behandlingstype = oppgave.behandlingstype,
        reservertAv = oppgave.reservertAv,
        reservertTidspunkt = oppgave.reservertTidspunkt,
        opprettetAv = oppgave.opprettetAv,
        opprettetTidspunkt = oppgave.opprettetTidspunkt,
        endretAv = oppgave.endretAv,
        endretTidspunkt = oppgave.endretTidspunkt,
        versjon = oppgave.versjon,
        harHasteMarkering = markeringer.any { it.markeringType === MarkeringForBehandling.HASTER },
        harAvslagSykdomMarkering = markeringer.any { it.markeringType === MarkeringForBehandling.AVSLAG_11_5 }
    )
}

/**
 * Planlegg jobb for å sende oppgaveoppdatering til statistikk. God ide å alltid legge et kall til denne
 * etter et write-kall til oppgave-repo.
 */
fun sendOppgaveStatusOppdatering(
    oppgaveId: OppgaveId, hendelseType: HendelseType, repository: FlytJobbRepository
) {
    repository.leggTil(
        JobbInput(StatistikkHendelseJobb).medParameter("hendelsesType", hendelseType.name)
            .medPayload(DefaultJsonMapper.toJson(oppgaveId))
            .forSak(oppgaveId.id)
    )
    logger.info("Sender oppgave-endring til statistikk-jobb. HendelseType: $hendelseType, oppgaveId: $oppgaveId.")
}