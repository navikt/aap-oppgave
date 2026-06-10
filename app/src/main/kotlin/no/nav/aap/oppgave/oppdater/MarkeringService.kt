package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.oppgave.markering.BehandlingMarkering
import no.nav.aap.oppgave.markering.MarkeringRepository
import no.nav.aap.oppgave.oppdater.hendelse.KELVIN
import no.nav.aap.oppgave.oppdater.hendelse.OppgaveOppdatering
import no.nav.aap.oppgave.oppdater.hendelse.ÅPNE_STATUSER
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.oppgave.verdityper.BehandlingMetadata
import no.nav.aap.oppgave.verdityper.Behandlingstype
import no.nav.aap.oppgave.verdityper.MarkeringForBehandling
import java.util.UUID

class MarkeringService(
    private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService(),
    private val markeringRepository: MarkeringRepository,
) {
    private val HASTEMARKERING_BEGRUNNELSE_SONING = "Ny soning, mulig stans"
    private val AVSLAG_11_5_BEGRUNNELSE = "Førstegangsbehandling er innstilt til avslag på § 11-5"

    fun oppdaterMarkeringer(oppgaveOppdatering: OppgaveOppdatering): List<Endring> {
        return listOf(
            oppdaterHastemarkeringForSoning(oppgaveOppdatering),
            oppdaterAvslagSykdomMarkering(oppgaveOppdatering)
        )
    }

    fun oppdaterAvslagSykdomMarkering(oppgaveOppdatering: OppgaveOppdatering): Endring {
        val referanse = oppgaveOppdatering.referanse
        val eksisterMarkering = finnEksisterendeAvslagSykdom(oppgaveOppdatering.referanse)
        val skalHaMarkering =
            oppgaveOppdatering.behandlingMetadata == BehandlingMetadata.AVSLAG_11_5_FØRSTEGANGSBEHANDLING

        return when {
            skalHaMarkering && !eksisterMarkering -> {
                markeringRepository.oppdaterMarkering(
                    referanse = referanse,
                    markering = BehandlingMarkering(
                        markeringType = MarkeringForBehandling.AVSLAG_11_5,
                        begrunnelse = AVSLAG_11_5_BEGRUNNELSE,
                        opprettetAv = KELVIN
                    )
                )
                Endring.OPPDATERT
            }

            !skalHaMarkering && eksisterMarkering -> {
                markeringRepository.slettMarkering(
                    referanse = referanse,
                    markering = BehandlingMarkering(
                        markeringType = MarkeringForBehandling.AVSLAG_11_5,
                        opprettetAv = KELVIN
                    )
                )
                Endring.SLETTET
            }

            else -> Endring.INGEN_ENDRING
        }
    }
    
    fun oppdaterHastemarkeringForSoning(oppgaveOppdatering: OppgaveOppdatering): Endring {
        if (!unleashService.isEnabled(FeatureToggles.SoningHastemarkering)) return Endring.INGEN_ENDRING

        val referanse = oppgaveOppdatering.referanse
        val eksistererSoningHaster = finnEksisterendeSoningHaster(oppgaveOppdatering.referanse)
        val skalHaSoningHaster = skalHaAutomatiskSoningHaster(oppgaveOppdatering)

        return when {
            skalHaSoningHaster && !eksistererSoningHaster -> {
                markeringRepository.oppdaterMarkering(
                    referanse = referanse,
                    markering = BehandlingMarkering(
                        markeringType = MarkeringForBehandling.HASTER,
                        begrunnelse = HASTEMARKERING_BEGRUNNELSE_SONING,
                        opprettetAv = KELVIN
                    )
                )
                Endring.OPPDATERT
            }

            !skalHaSoningHaster && eksistererSoningHaster -> {
                markeringRepository.slettMarkering(
                    referanse = referanse,
                    markering = BehandlingMarkering(
                        markeringType = MarkeringForBehandling.HASTER,
                        opprettetAv = KELVIN
                    )
                )
                Endring.SLETTET
            }

            else -> Endring.INGEN_ENDRING
        }
    }

    private fun finnEksisterendeSoningHaster(referanse: UUID): Boolean =
        markeringRepository
            .hentMarkeringerForBehandling(referanse)
            .any {
                it.begrunnelse == HASTEMARKERING_BEGRUNNELSE_SONING &&
                        it.opprettetAv == KELVIN &&
                        it.markeringType == MarkeringForBehandling.HASTER
            }

    private fun finnEksisterendeAvslagSykdom(referanse: UUID): Boolean =
        markeringRepository
            .hentMarkeringerForBehandling(referanse)
            .any {
                it.begrunnelse == AVSLAG_11_5_BEGRUNNELSE &&
                        it.opprettetAv == KELVIN &&
                        it.markeringType == MarkeringForBehandling.AVSLAG_11_5
            }

    private fun skalHaAutomatiskSoningHaster(oppgaveOppdatering: OppgaveOppdatering): Boolean =
        oppgaveOppdatering.behandlingstype == Behandlingstype.REVURDERING &&
                oppgaveOppdatering.avklaringsbehov.any {
                    it.avklaringsbehovKode.kode == Definisjon.AVKLAR_SONINGSFORRHOLD.kode.name &&
                            it.status in ÅPNE_STATUSER
                }
}

enum class Endring {
    OPPDATERT,
    SLETTET,
    INGEN_ENDRING;

    fun erEndret() = this != INGEN_ENDRING
}