package no.nav.aap.oppgave.opprette

import no.nav.aap.behandlingsflyt.kontrakt.behandling.Status
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.oppgave.AvklaringsbehovReferanseDto
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.plukk.ReserverOppgaveService
import no.nav.aap.oppgave.verdityper.AvklaringsbehovKode
import org.slf4j.LoggerFactory

class OppdaterOppgaveService(private val connection: DBConnection) {

    private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)

    fun oppdaterOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse, token: OidcToken) {
        val oppgaveRepo = OppgaveRepository(connection)
        val eksisterendeOppgaver = oppgaveRepo.hentOppgaver(behandlingFlytStoppetHendelse.saksnummer.toString(), behandlingFlytStoppetHendelse.referanse.referanse, null)

        val oppgaveMap = eksisterendeOppgaver.associateBy( {it.avklaringsbehovKode}, {it} )

        when (behandlingFlytStoppetHendelse.status) {
            Status.AVSLUTTET -> avslutteOppgaver(eksisterendeOppgaver, oppgaveRepo)
            else -> oppdaterOppgaver(behandlingFlytStoppetHendelse, oppgaveMap, oppgaveRepo, token)
        }
    }

    private fun oppdaterOppgaver(
        behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse,
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        oppgaveRepo: OppgaveRepository,
        token: OidcToken
    ) {
        val åpneAvklaringsbehov = behandlingFlytStoppetHendelse.avklaringsbehov.filter {it.status != no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET}
        val avsluttedeAvklaringsbehov = behandlingFlytStoppetHendelse.avklaringsbehov.filter {it.status == no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.AVSLUTTET}

        // Opprett nye oppgaver
        val avklarsbehovSomDetSkalOpprettesOppgaverFor = åpneAvklaringsbehov.filter { oppgaveMap[AvklaringsbehovKode(it.definisjon.type)] == null}
        opprettOppgaver(behandlingFlytStoppetHendelse, avklarsbehovSomDetSkalOpprettesOppgaverFor, oppgaveRepo, token)

        // Gjenåpne avsluttede oppgaver
        åpneAvklaringsbehov.forEach { avklaringsbehov ->
            gjenåpneOppgave(oppgaveMap, avklaringsbehov, oppgaveRepo)
        }

        // Avslutt oppgaver hvor avklaringsbehovet er lukket
        val oppgaverSomSkalAvsluttes = avsluttedeAvklaringsbehov.mapNotNull { oppgaveMap[AvklaringsbehovKode(it.definisjon.type)] }
        avslutteOppgaver(oppgaverSomSkalAvsluttes, oppgaveRepo)
    }

    private fun gjenåpneOppgave(
        oppgaveMap: Map<AvklaringsbehovKode, OppgaveDto>,
        avklaringsbehov: AvklaringsbehovHendelseDto,
        oppgaveRepo: OppgaveRepository
    ) {
        val eksisterendeOppgave = oppgaveMap[AvklaringsbehovKode(avklaringsbehov.definisjon.type)]
        if (eksisterendeOppgave != null && eksisterendeOppgave.status == no.nav.aap.oppgave.verdityper.Status.AVSLUTTET) {
            oppgaveRepo.gjenåpneOppgave(eksisterendeOppgave.id!!, "Kelvin")
            if (avklaringsbehov.status in setOf(
                    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status.SENDT_TILBAKE_FRA_BESLUTTER)
            ) {
                val sistEndretAv = avklaringsbehov.sistEndretAv()
                if (sistEndretAv != null && sistEndretAv != "Kelvin") {
                    oppgaveRepo.reserverOppgave(eksisterendeOppgave.id!!, "Kelvin", sistEndretAv)
                }
            }
        }
    }

    private fun opprettOppgaver(behandlingFlytStoppetHendelse: BehandlingFlytStoppetHendelse, avklarsbehovSomDetSkalOpprettesOppgaverFor: List<AvklaringsbehovHendelseDto>, oppgaveRepo: OppgaveRepository, token: OidcToken) {

        val hvemLøsteForrigeAvklaringsbehovIdent = behandlingFlytStoppetHendelse.hvemLøsteForrigeAvklaringsbehov()


        avklarsbehovSomDetSkalOpprettesOppgaverFor.forEach {
            val nyOppgave = behandlingFlytStoppetHendelse.opprettNyOppgave(it, "Kelvin")
            oppgaveRepo.opprettOppgave(nyOppgave)
            if (hvemLøsteForrigeAvklaringsbehovIdent != null) {
                val avklaringsbehovReferanse = AvklaringsbehovReferanseDto(
                    saksnummer = behandlingFlytStoppetHendelse.saksnummer.toString(),
                    referanse = behandlingFlytStoppetHendelse.referanse.referanse,
                    null,
                    AvklaringsbehovKode(it.definisjon.type)
                )
                val reserverteOppgaver = ReserverOppgaveService(connection).reserverOppgave(avklaringsbehovReferanse, hvemLøsteForrigeAvklaringsbehovIdent, token)
                if (reserverteOppgaver.isNotEmpty()) {
                    log.info("Ny oppgave ble automatisk tilordnet: $hvemLøsteForrigeAvklaringsbehovIdent")
                }
            }
        }
    }

    private fun avslutteOppgaver(oppgaver: List<OppgaveDto>, oppgaveRepo: OppgaveRepository) {
        oppgaver
            .filter { it.status != no.nav.aap.oppgave.verdityper.Status.AVSLUTTET }
            .forEach { oppgaveRepo.avsluttOppgave(it.id!!, "Kelvin") }
    }

}