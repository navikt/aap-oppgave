package no.nav.aap.oppgave.markering

import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.TestDataSource
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.OppgaveRepository
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import no.nav.aap.oppgave.verdityper.MarkeringHendelseType
import no.nav.aap.oppgave.verdityper.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MarkeringRepositoryTest {
    private lateinit var dataSource: TestDataSource

    @BeforeEach
    fun setup() {
        dataSource = TestDataSource()
    }

    @AfterEach
    fun tearDown() = dataSource.close()

    @Test
    fun `kan lagre og hente markeringer på behandling`() {
        val behandlingId = UUID.randomUUID()

        val hasterMarkering = BehandlingMarkering(
            markeringType = MarkeringForBehandling.HASTER,
            begrunnelse = "begrunnelseHaster",
            opprettetAv = "saksbehandler",
            opprettetTidspunkt = LocalDateTime.now(),
            hendelseType = MarkeringHendelseType.OPPRETTET
        )

        val avslag115Markering = BehandlingMarkering(
            markeringType = MarkeringForBehandling.AVSLAG_11_5,
            begrunnelse = "begrunnelseAvslag115",
            opprettetAv = "saksbehandler",
            opprettetTidspunkt = LocalDateTime.now()
        )

        dataSource.transaction { connection ->
            val markeringRepository = MarkeringRepository(connection)
            // lagre avslag_11-5-makering
            markeringRepository.lagreMarkeringHendelse(behandlingId, avslag115Markering)
            val hentetMarkering = markeringRepository.hentGjeldendeMarkeringerForBehandling(behandlingId)
            assertThat(hentetMarkering).hasSize(1)
            assertThat(hentetMarkering.first().markeringType).isEqualTo(MarkeringForBehandling.AVSLAG_11_5)
            assertThat(hentetMarkering.first().begrunnelse).isEqualTo(avslag115Markering.begrunnelse)

            // lagre hastemarkering
            markeringRepository.lagreMarkeringHendelse(behandlingId, hasterMarkering)
            val markeringer = markeringRepository.hentGjeldendeMarkeringerForBehandling(behandlingId)
            assertThat(markeringer).hasSize(2)
            assertThat(markeringer.map { it.markeringType }).containsExactlyInAnyOrder(
                MarkeringForBehandling.AVSLAG_11_5,
                MarkeringForBehandling.HASTER
            )
            assertThat(markeringer.map { it.begrunnelse }).containsExactlyInAnyOrder(
                "begrunnelseAvslag115",
                "begrunnelseHaster"
            )
        }
    }

    @Test
    fun `hentMarkeringerOgHistorikk returnerer markeringer på tvers av behandlinger for et saksnummer`() {
        val saksnummer = "SAK123"
        val behandlingRef1 = UUID.randomUUID()
        val behandlingRef2 = UUID.randomUUID()

        dataSource.transaction { connection ->
            val oppgaveRepository = OppgaveRepository(connection)
            val markeringRepository = MarkeringRepository(connection)

            // Opprett oppgaver som knytter behandlingene til samme saksnummer
            oppgaveRepository.opprettOppgave(
                OppgaveDto(
                    saksnummer = saksnummer,
                    behandlingRef = behandlingRef1,
                    enhet = "0100",
                    oppfølgingsenhet = null,
                    behandlingOpprettet = LocalDateTime.now().minusDays(3),
                    avklaringsbehovKode = "1000",
                    status = Status.OPPRETTET,
                    behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
                    opprettetAv = "bruker1",
                    opprettetTidspunkt = LocalDateTime.now(),
                )
            )
            oppgaveRepository.opprettOppgave(
                OppgaveDto(
                    saksnummer = saksnummer,
                    behandlingRef = behandlingRef2,
                    enhet = "0100",
                    oppfølgingsenhet = null,
                    behandlingOpprettet = LocalDateTime.now().minusDays(2),
                    avklaringsbehovKode = "1000",
                    status = Status.OPPRETTET,
                    behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING,
                    opprettetAv = "bruker1",
                    opprettetTidspunkt = LocalDateTime.now(),
                )
            )

            // Lagre markeringer på behandling 1
            markeringRepository.lagreMarkeringHendelse(
                behandlingRef1,
                BehandlingMarkering(
                    markeringType = MarkeringForBehandling.HASTER,
                    begrunnelse = "haster veldig",
                    opprettetAv = "saksbehandler1",
                    opprettetTidspunkt = LocalDateTime.now().minusHours(3),
                    hendelseType = MarkeringHendelseType.OPPRETTET,
                )
            )
            markeringRepository.lagreMarkeringHendelse(
                behandlingRef1,
                BehandlingMarkering(
                    markeringType = MarkeringForBehandling.HASTER,
                    begrunnelse = "haster ikke lenger",
                    opprettetAv = "saksbehandler2",
                    opprettetTidspunkt = LocalDateTime.now().minusHours(2),
                    hendelseType = MarkeringHendelseType.FJERNET,
                )
            )

            // Lagre markering på behandling 2
            markeringRepository.lagreMarkeringHendelse(
                behandlingRef2,
                BehandlingMarkering(
                    markeringType = MarkeringForBehandling.AVSLAG_11_5,
                    begrunnelse = "trenger spesialist",
                    opprettetAv = "saksbehandler1",
                    opprettetTidspunkt = LocalDateTime.now().minusHours(1),
                    hendelseType = MarkeringHendelseType.OPPRETTET,
                )
            )

            // Hent markeringer på tvers av begge behandlinger via saksnummer
            val resultat = markeringRepository.hentMarkeringerOgHistorikk(Saksnummer(saksnummer))

            // Skal inneholde markeringer fra begge behandlinger
            assertThat(resultat).hasSize(3)
            assertThat(resultat.map { it.behandlingRef }).contains(
                behandlingRef1.toString(),
                behandlingRef2.toString()
            )

            // Skal inneholde markeringer fra behandling 1 (HASTER OPPRETTET + FJERNET)
            val markeringerBehandling1 = resultat.filter { it.behandlingRef == behandlingRef1.toString() }
            assertThat(markeringerBehandling1).hasSize(2)
            assertThat(markeringerBehandling1.map { it.hendelseType }).containsExactlyInAnyOrder(
                MarkeringHendelseType.OPPRETTET,
                MarkeringHendelseType.FJERNET,
            )

            // Skal inneholde markering fra behandling 2 (AVSLAG_11_5)
            val markeringerBehandling2 = resultat.filter { it.behandlingRef == behandlingRef2.toString() }
            assertThat(markeringerBehandling2).hasSize(1)
            assertThat(markeringerBehandling2.first().markeringType).isEqualTo(MarkeringForBehandling.AVSLAG_11_5)

            // Verifiser at resultatet er sortert nyeste først
            val tidspunkter = resultat.map { it.opprettetTidspunkt }
            assertThat(tidspunkter).isSortedAccordingTo(Comparator.reverseOrder())
        }
    }

    @Test
    fun `hentMarkeringerOgHistorikk returnerer tom liste når saksnummer ikke finnes`() {
        dataSource.transaction { connection ->
            val markeringRepository = MarkeringRepository(connection)
            val resultat = markeringRepository.hentMarkeringerOgHistorikk(Saksnummer("FINNES_IKKE"))
            assertThat(resultat).isEmpty()
        }
    }
}