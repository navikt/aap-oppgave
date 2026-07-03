package no.nav.aap.oppgave.oppdater.hendelse

import no.nav.aap.behandlingsflyt.kontrakt.behandling.BehandlingReferanse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.TilbakekrevingsbehandlingOppdatertHendelse
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingBehandlingsstatus
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.TilbakekrevingVenteGrunn
import no.nav.aap.behandlingsflyt.kontrakt.sak.Saksnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class TilbakekrevingMappingTest {

    @Test
    fun `venteInformasjon er null når behandlingen ikke er på vent`() {
        val hendelseIkkePåVent = stubTilbakekrevingsHendelse().copy(
            gjenopptas = null,
            venteGrunn = null,
        )

        val oppdatering = hendelseIkkePåVent.tilOppgaveOppdatering()

        assertThat(oppdatering.venteInformasjon).isNull()
    }

    @Test
    fun `venteInformasjon inneholder frist og årsak når behandlingen er på vent`() {
        val gjenopptas = LocalDate.now().plusMonths(1)
        val hendelsePåVent = stubTilbakekrevingsHendelse().copy(
            gjenopptas = gjenopptas,
            venteGrunn = TilbakekrevingVenteGrunn.AVVENTER_BRUKERUTTALELSE,
        )

        val oppdatering = hendelsePåVent.tilOppgaveOppdatering()

        assertThat(oppdatering.venteInformasjon).isNotNull()
        assertThat(oppdatering.venteInformasjon!!.frist).isEqualTo(gjenopptas)
        assertThat(oppdatering.venteInformasjon!!.årsakTilSattPåVent).isEqualTo("AVVENTER_BRUKERUTTALELSE")
        assertThat(oppdatering.venteInformasjon!!.sattPåVentAv).isEqualTo(TILBAKEKREVING)
        assertThat(oppdatering.venteInformasjon!!.begrunnelse).isNull()
    }

    @Test
    fun `venteInformasjon har null-årsak når gjenopptas er satt men venteGrunn mangler`() {
        val hendelseMedFristUtenGrunn = stubTilbakekrevingsHendelse().copy(
            gjenopptas = LocalDate.now().plusMonths(1),
            venteGrunn = null,
        )

        val oppdatering = hendelseMedFristUtenGrunn.tilOppgaveOppdatering()

        assertThat(oppdatering.venteInformasjon).isNotNull()
        assertThat(oppdatering.venteInformasjon!!.årsakTilSattPåVent).isNull()
    }

    @Test
    fun `venteInformasjon er null for TIL_GODKJENNING selv om gjenopptas og venteGrunn er satt`() {
        val hendelseTilGodkjenning = stubTilbakekrevingsHendelse().copy(
            behandlingStatus = TilbakekrevingBehandlingsstatus.TIL_GODKJENNING,
            gjenopptas = LocalDate.now().plusMonths(1),
            venteGrunn = TilbakekrevingVenteGrunn.AVVENTER_BRUKERUTTALELSE,
        )

        val oppdatering = hendelseTilGodkjenning.tilOppgaveOppdatering()

        assertThat(oppdatering.venteInformasjon).isNull()
    }

    @Test
    fun `venteInformasjon er null for TIL_BESLUTTER selv om gjenopptas og venteGrunn er satt`() {
        val hendelseTilBeslutter = stubTilbakekrevingsHendelse().copy(
            behandlingStatus = TilbakekrevingBehandlingsstatus.TIL_BESLUTTER,
            gjenopptas = LocalDate.now().plusMonths(1),
            venteGrunn = TilbakekrevingVenteGrunn.AVVENTER_BRUKERUTTALELSE,
        )

        val oppdatering = hendelseTilBeslutter.tilOppgaveOppdatering()

        assertThat(oppdatering.venteInformasjon).isNull()
    }

    @Test
    fun `venteInformasjon populeres fortsatt for saksbehandler-steg når gjenopptas er satt`() {
        val gjenopptas = LocalDate.now().plusMonths(1)
        val statuserForSaksbehandlerSteg = listOf(
            TilbakekrevingBehandlingsstatus.OPPRETTET,
            TilbakekrevingBehandlingsstatus.TIL_FORHÅNDSVARSEL,
            TilbakekrevingBehandlingsstatus.RETUR_FRA_BESLUTTER,
        )

        statuserForSaksbehandlerSteg.forEach { status ->
            val hendelse = stubTilbakekrevingsHendelse().copy(
                behandlingStatus = status,
                gjenopptas = gjenopptas,
                venteGrunn = TilbakekrevingVenteGrunn.AVVENTER_BRUKERUTTALELSE,
            )

            val oppdatering = hendelse.tilOppgaveOppdatering()

            assertThat(oppdatering.venteInformasjon).isNotNull()
            assertThat(oppdatering.venteInformasjon!!.frist).isEqualTo(gjenopptas)
        }
    }

    private fun stubTilbakekrevingsHendelse() = TilbakekrevingsbehandlingOppdatertHendelse(
        personIdent = "12345678901",
        saksnummer = Saksnummer("SAK".plus(Random.nextLong())),
        behandlingref = BehandlingReferanse(UUID.randomUUID()),
        behandlingStatus = TilbakekrevingBehandlingsstatus.TIL_BEHANDLING,
        sakOpprettet = LocalDateTime.now(),
        totaltFeilutbetaltBeløp = BigDecimal("10000"),
        saksbehandlingURL = "https://nav.no/behandling/123",
    )

}
