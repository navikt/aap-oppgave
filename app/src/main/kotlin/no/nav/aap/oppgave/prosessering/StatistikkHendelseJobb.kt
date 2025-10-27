package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.motor.FlytJobbRepository
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.markering.BehandlingMarkering
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveHendelse
import no.nav.aap.oppgave.statistikk.OppgaveTilStatistikkDto
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("prosessering.StatistikkHendelseJobb")

class StatistikkHendelseJobb(private val oppgaveRepository: OppgaveRepository, private val markeringRepository: MarkeringRepository) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelsesType = HendelseType.valueOf(input.parameter("hendelsesType"))
        val oppgaveId = DefaultJsonMapper.fromJson<OppgaveId>(input.payload())

        oppgaveRepository.hentOppgave(oppgaveId.id).let { oppgaveDto ->
            val behandlingsReferanse = requireNotNull(oppgaveDto.behandlingRef) {
                "Kan ikke sende hendelse til statistikk for oppgave som mangler behandlingsreferanse. OppgaveId: ${oppgaveDto.id}"
            }
            val markeringer = markeringRepository.hentMarkeringerForBehandling(behandlingsReferanse)
            StatistikkGateway.avgiHendelse(
                OppgaveHendelse(
                    hendelse = hendelsesType,
                    oppgaveTilStatistikkDto = fraOppgaveDto(oppgaveDto, markeringer)
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

private fun fraOppgaveDto(oppgaveDto: OppgaveDto, markeringer: List<BehandlingMarkering>): OppgaveTilStatistikkDto {
    return OppgaveTilStatistikkDto(
        id = oppgaveDto.id,
        personIdent = oppgaveDto.personIdent,
        saksnummer = oppgaveDto.saksnummer,
        behandlingRef = oppgaveDto.behandlingRef,
        journalpostId = oppgaveDto.journalpostId,
        enhet = oppgaveDto.enhetForKø,
        avklaringsbehovKode = oppgaveDto.avklaringsbehovKode,
        status = oppgaveDto.status,
        behandlingstype = oppgaveDto.behandlingstype,
        reservertAv = oppgaveDto.reservertAv,
        reservertTidspunkt = oppgaveDto.reservertTidspunkt,
        opprettetAv = oppgaveDto.opprettetAv,
        opprettetTidspunkt = oppgaveDto.opprettetTidspunkt,
        endretAv = oppgaveDto.endretAv,
        endretTidspunkt = oppgaveDto.endretTidspunkt,
        versjon = oppgaveDto.versjon,
        harHasteMarkering = markeringer.any { it.markeringType === MarkeringForBehandling.HASTER }
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
    )
    logger.info("Sender oppgave-endring til statistikk-jobb. HendelseType: $hendelseType, oppgaveId: $oppgaveId.")
}