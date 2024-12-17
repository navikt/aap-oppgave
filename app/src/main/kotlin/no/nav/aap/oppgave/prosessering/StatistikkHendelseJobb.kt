package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.json.DefaultJsonMapper
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.statistikk.HendelseType
import no.nav.aap.oppgave.statistikk.OppgaveHendelse

class StatistikkHendelseJobb(private val oppgaveRepository: OppgaveRepository) : JobbUtfører {
    override fun utfør(input: JobbInput) {
        val hendelsesType = HendelseType.valueOf(input.parameter("hendelsesType"))
        val oppgaveId = DefaultJsonMapper.fromJson<OppgaveId>(input.payload())

        oppgaveRepository.hentOppgave(oppgaveId).let { oppgaveDto ->
            StatistikkGateway.avgiHendelse(
                OppgaveHendelse(
                    hendelse = hendelsesType,
                    oppgaveDto = oppgaveDto
                )
            )
        }
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return StatistikkHendelseJobb(OppgaveRepository(connection))
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