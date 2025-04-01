package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Status as AvklaringsbehovStatus
import  no.nav.aap.behandlingsflyt.kontrakt.behandling.Status as BehandlingStatus
import no.nav.aap.behandlingsflyt.kontrakt.behandling.TypeBehandling
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.BehandlingFlytStoppetHendelse
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveId
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.klienter.msgraph.Group
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.msgraph.MemberOf
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.AvklaringsbehovHendelseDto
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.EndringDTO
import no.nav.aap.oppgave.enhet.EnhetForOppgave
import no.nav.aap.oppgave.enhet.IEnhetService
import no.nav.aap.oppgave.klienter.norg.Diskresjonskode
import no.nav.aap.oppgave.klienter.oppfolging.IVeilarbarboppfolgingKlient
import no.nav.aap.oppgave.verdityper.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.AfterTest

class OppdaterOppgaveServiceTest {

    @AfterTest
    fun tearDown() {
        @Suppress("SqlWithoutWhere")
        InitTestDatabase.dataSource.transaction {
            it.execute("DELETE FROM OPPGAVE_HISTORIKK")
            it.execute("DELETE FROM OPPGAVE")
        }
    }


    @Test
    fun `Ved gjenåpning skal oppgaven bli reservert på personen som løste avklaringsbehovet`() {
        val (oppgaveId, saksnummer, behandlingsref) = opprettOppgave(status = Status.AVSLUTTET,
            enhet = ENHET_NAV_LØRENSKOG,
            avklaringsbehovKode = AvklaringsbehovKode(Definisjon.AVKLAR_SYKDOM.kode.name))

        val nå = LocalDateTime.now()

        val hendelse = BehandlingFlytStoppetHendelse(
            personIdent = "12345678901",
            saksnummer = saksnummer,
            referanse = behandlingsref,
            status = BehandlingStatus.UTREDES,
            opprettetTidspunkt = LocalDateTime.now(),
            behandlingType = TypeBehandling.Førstegangsbehandling,
            versjon = "Kelvin 1.0",
            hendelsesTidspunkt = nå,
            avklaringsbehov = listOf(
                AvklaringsbehovHendelseDto(
                    avklaringsbehovDefinisjon = Definisjon.AVKLAR_SYKDOM,
                    status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                    endringer = listOf(
                        EndringDTO(
                            status = AvklaringsbehovStatus.OPPRETTET,
                            endretAv = "Kelvin",
                            tidsstempel = nå.minusHours(2)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.AVSLUTTET,
                            endretAv = "Saksbehandler",
                            tidsstempel = nå.minusHours(1)
                        ),
                        EndringDTO(
                            status = AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER,
                            endretAv = "Kvalitetssikrer",
                            tidsstempel = nå
                        )
                    )
                ),
            )
        )


        //Utfør
        InitTestDatabase.dataSource.transaction { connection ->
            OppdaterOppgaveService(
                connection,
                graphClient,
                veilarbarboppfolgingKlient,
                enhetService
            ).oppdaterOppgaver(hendelse.tilOppgaveOppdatering())
        }

        val oppgave = hentOppgave(oppgaveId)
        assertThat(oppgave.reservertAv).isEqualTo("Saksbehandler")
    }

    private val ENHET_NAV_LØRENSKOG = "0230"
    private fun opprettOppgave(
        saksnummer: String = "123",
        behandlingRef: UUID = UUID.randomUUID(),
        status: Status = Status.OPPRETTET,
        avklaringsbehovKode: AvklaringsbehovKode = AvklaringsbehovKode("1000"),
        behandlingstype: Behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
        enhet: String = ENHET_NAV_LØRENSKOG,
        oppfølgingsenhet: String? = null,
        veileder: String? = null,
    ):  Triple<OppgaveId, Saksnummer, BehandlingReferanse> {
        val oppgaveDto = OppgaveDto(
            saksnummer = saksnummer,
            behandlingRef = behandlingRef,
            enhet = enhet,
            oppfølgingsenhet = oppfølgingsenhet,
            behandlingOpprettet = LocalDateTime.now().minusDays(3),
            avklaringsbehovKode = avklaringsbehovKode.kode,
            status = status,
            behandlingstype = behandlingstype,
            opprettetAv = "Kelvin",
            veileder = veileder,
            opprettetTidspunkt = LocalDateTime.now(),
        )
        val oppgaveId = InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).opprettOppgave(oppgaveDto)
        }
        return Triple(oppgaveId, Saksnummer(saksnummer), BehandlingReferanse(behandlingRef))
    }

    private fun reserverOppgave(oppgaveId: OppgaveId, ident: String) {
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).reserverOppgave(oppgaveId, ident, ident)
        }
    }

    private fun hentOppgave(oppgaveId: OppgaveId): OppgaveDto {
        return InitTestDatabase.dataSource.transaction { connection ->
            OppgaveRepository(connection).hentOppgave(oppgaveId)
        }
    }

    val graphClient = object : IMsGraphClient {
        override fun hentEnhetsgrupper(currentToken: String, ident: String): MemberOf {
            return MemberOf(
                groups = listOf(
                    Group(name = "0000-GA-ENHET_$ENHET_NAV_LØRENSKOG", id = UUID.randomUUID()),
                )
            )
        }

    }

    val veilarbarboppfolgingKlient = object : IVeilarbarboppfolgingKlient {
        override fun hentVeileder(personIdent: String) = null
    }
    
    val enhetService = object : IEnhetService {
        override fun hentEnheter(currentToken: String, ident: String): List<String> {
            TODO("Not yet implemented")
        }

        override fun finnEnhetForOppgave(fnr: String?): EnhetForOppgave {
            return EnhetForOppgave(enhet = ENHET_NAV_LØRENSKOG, null)
        }

        override fun finnFortroligAdresse(fnr: String): Diskresjonskode {
            TODO("Not yet implemented")
        }
    }
}