package no.nav.aap.oppgave.prosessering

import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører
import no.nav.aap.motor.cron.CronExpression
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class VarsleOmOppgaverIkkeEndretJobb(
    private val oppgaveRepository: OppgaveRepository,
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService()
) : JobbUtfører {
    private val log = LoggerFactory.getLogger(VarsleOmOppgaverIkkeEndretJobb::class.java)

    override fun utfør(input: JobbInput) {
        val alleÅpneOppgaverIkkePåVent = oppgaveRepository.hentAlleÅpneOppgaver().filter { it.påVentTil == null }
        log.info("Fant ${alleÅpneOppgaverIkkePåVent.size} åpne oppgaver som ikke er på vent")
        val nå = LocalDateTime.now()
        val oppgaverIkkeEndretPå7Dager = alleÅpneOppgaverIkkePåVent.filter { (it.endretTidspunkt != null && it.endretTidspunkt!! < nå.minusDays(7)) || (it.endretTidspunkt == null && it.opprettetTidspunkt < nå.minusDays(7)) }

        log.info("Fant ${oppgaverIkkeEndretPå7Dager.size} oppgaver som ikke er endret på mer enn 7 dager")
        if (unleashService.isEnabled(FeatureToggles.VarsleOmOppgaverEldreEnn7Dager)) {
            oppgaverIkkeEndretPå7Dager.forEach {
                log.error(
                    "Oppgave ${
                        OppgaveId(
                            it.id!!,
                            it.versjon
                        )
                    } for avklaringsbehov ${it.avklaringsbehovKode} på enhet ${it.enhetForKø} er ikke endret siden ${it.endretTidspunkt ?: it.opprettetTidspunkt}. Saksnummer: ${it.saksnummer}"
                )
            }
        }
    }

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            return VarsleOmOppgaverIkkeEndretJobb(OppgaveRepository(connection))
        }

        override fun type() = "oppgave.varsleOmOppgaverIkkeEndret"
        override fun navn() = "Varsle om oppgaver som ikke er endret siste uka."
        override fun beskrivelse() = "Logger error for alle oppgaver som ikke er endret siste uka."

        override fun cron(): CronExpression {
            return CronExpression.create("0 0 6 * * *")
        }
    }

}