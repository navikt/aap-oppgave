package no.nav.aap.oppgave.enhet

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class EnhetOgOversendelseTest {

    @Test
    fun `hvis ingen oppgaver returneres null`() {
        val res = enhetOgOversendelse(listOf(), emptyList())

        assertThat(res).isNull()
    }

    @Test
    fun `viser venteårsak når det er flere oppgaver`() {
        val res =
            enhetOgOversendelse(
                listOf(
                    oppgave().copy(status = Status.AVSLUTTET),
                    oppgave().copy(påVentÅrsak = "FORDI_BARE").åpen()
                ),
                emptyList()
            )

        assertThat(res?.venteÅrsak).isEqualTo("FORDI_BARE")
    }

    @Test
    fun `om behandlingen går til NAY flere ganger, skal oversendelse være første tidspunkt i siste bolk`() {
        val oppgaver =
            listOf(
                oppgave(Definisjon.AVKLAR_SYKDOM, "1234"),
                oppgave(Definisjon.KVALITETSSIKRING, "1200"),
                oppgave(Definisjon.SAMORDNING_ARBEIDSGIVER, "4491"),
                oppgave(Definisjon.AVKLAR_SYKDOM, "1234"),
                oppgave(Definisjon.SAMORDNING_ARBEIDSGIVER, "4491"),
                oppgave(Definisjon.SAMORDNING_ARBEIDSGIVER, "4491").åpen(),
            )

        val res = enhetOgOversendelse(oppgaver, emptyList())!!

        assertThat(res.oversendtDato).isEqualTo(LocalDate.of(2022, 1, 1).plusDays(4))
    }

    @Test
    fun `for beslutter-oppgaver, vis når saken først gikk til NAY`() {
        val oppgaver =
            listOf(
                oppgave(Definisjon.AVKLAR_SYKDOM, "1234"),
                oppgave(Definisjon.KVALITETSSIKRING, "1200"),
                oppgave(Definisjon.SAMORDNING_ARBEIDSGIVER, "4491"),
                oppgave(Definisjon.AVKLAR_SYKDOM, "1234"),
                oppgave(Definisjon.SAMORDNING_ARBEIDSGIVER, "4491"),
                oppgave(Definisjon.FATTE_VEDTAK, "4491").åpen(),
            )

        val res = enhetOgOversendelse(oppgaver, emptyList())!!

        assertThat(res.oversendtDato).isEqualTo(LocalDate.of(2022, 1, 1).plusDays(4))
    }

    private val behandlingRef = UUID.randomUUID()

    private val tid = generateSequence(LocalDateTime.of(2022, 1, 1, 12, 0)) { it.plusDays(1) }.iterator()

    fun oppgave(avklaringsbehov: Definisjon = Definisjon.AVKLAR_SYKDOM, enhet: String = "1234") = OppgaveDto(
        enhet = enhet,
        saksnummer = "SAK",
        behandlingRef = behandlingRef,
        veilederArbeid = null,
        veilederSykdom = null,
        behandlingOpprettet = LocalDate.of(2022, 1, 1).atStartOfDay(),
        avklaringsbehovKode = avklaringsbehov.name,
        status = Status.AVSLUTTET,
        påVentTil = null,
        påVentÅrsak = null,
        utløptVentefrist = null,
        opprettetAv = "Noen",
        opprettetTidspunkt = tid.next(),
        oppfølgingsenhet = null,
        behandlingstype = Behandlingstype.FØRSTEGANGSBEHANDLING
    )

    private fun OppgaveDto.åpen() = this.copy(status = Status.OPPRETTET)

}