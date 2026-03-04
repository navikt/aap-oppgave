package no.nav.aap.oppgave.oppdater

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER
import no.nav.aap.oppgave.ReturInformasjon
import no.nav.aap.oppgave.ReturStatus
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovHendelse
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovStatus
import no.nav.aap.oppgave.oppdater.hendelse.OppgaveOppdatering
import no.nav.aap.oppgave.ÅrsakTilReturKode
import org.slf4j.LoggerFactory

class ReturInformasjonUtleder {
    private val log = LoggerFactory.getLogger(ReturInformasjonUtleder::class.java)

    fun utledReturInformasjon(
        avklaringsbehov: AvklaringsbehovHendelse,
        oppgaveOppdatering: OppgaveOppdatering
    ): ReturInformasjon? {
        return utledReturFraToTrinn(avklaringsbehov) ?: utledReturTilToTrinn(avklaringsbehov, oppgaveOppdatering)
    }

    private fun utledReturFraToTrinn(avklaringsbehov: AvklaringsbehovHendelse): ReturInformasjon? {
        val status = when (avklaringsbehov.status) {
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_BESLUTTER -> ReturStatus.RETUR_FRA_BESLUTTER
            AvklaringsbehovStatus.SENDT_TILBAKE_FRA_KVALITETSSIKRER -> ReturStatus.RETUR_FRA_KVALITETSSIKRER
            else -> null
        }
        if (status == null) return null

        val sisteEndring = avklaringsbehov.sisteEndring()
        return ReturInformasjon(
            status = status,
            årsaker = sisteEndring.årsakTilRetur.map { ÅrsakTilReturKode.valueOf(it.name) },
            begrunnelse = requireNotNull(sisteEndring.begrunnelse) { "Det skal alltid finnes begrunnelse for retur." },
            endretAv = sisteEndring.endretAv,
        )
    }

    private fun utledReturTilToTrinn(
        avklaringsbehov: AvklaringsbehovHendelse,
        oppgaveOppdatering: OppgaveOppdatering,
    ): ReturInformasjon? {
        // Setter ReturInformasjon når behandling sendes tilbake til totrinn
        return if (erReturTilToTrinn(avklaringsbehov)) {
            log.info("Totrinnsoppgave gjenåpnet, setter retur fra veileder/saksbehandler. Saksnummer: ${oppgaveOppdatering.saksnummer}")
            val hvemLøsteForrigeAvklaringsbehov = oppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov()
            val forrigeAvklaringsbehovLøstAvVeileder =
                hvemLøsteForrigeAvklaringsbehov?.first?.kode in AVKLARINGSBEHOV_FOR_VEILEDER.map { it.kode }
            ReturInformasjon(
                status = if (forrigeAvklaringsbehovLøstAvVeileder) {
                    ReturStatus.RETUR_FRA_VEILEDER
                } else {
                    ReturStatus.RETUR_FRA_SAKSBEHANDLER
                },
                årsaker = emptyList(),
                endretAv = hvemLøsteForrigeAvklaringsbehov?.second ?: "Ukjent",
                begrunnelse = if (forrigeAvklaringsbehovLøstAvVeileder) {
                    "Retur fra veileder"
                } else {
                    "Retur fra saksbehandler"
                }
            )
        } else {
            null
        }
    }

    private fun erReturTilToTrinn(avklaringsbehov: AvklaringsbehovHendelse): Boolean {
        return (avklaringsbehov.avklaringsbehovKode.kode in setOf(
            Definisjon.KVALITETSSIKRING.kode.name,
            Definisjon.FATTE_VEDTAK.kode.name,
        ) && avklaringsbehov.endringer.any { it.status == AvklaringsbehovStatus.AVSLUTTET })
    }
}