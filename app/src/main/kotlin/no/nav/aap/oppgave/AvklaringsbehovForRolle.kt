package no.nav.aap.oppgave

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon.BehovType
import no.nav.aap.behandlingsflyt.kontrakt.steg.StegType
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.oppgave.unleash.FeatureToggles
import no.nav.aap.oppgave.unleash.IUnleashService
import no.nav.aap.oppgave.unleash.UnleashServiceProvider
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import no.nav.aap.tilgang.Rolle

private val unleashService: IUnleashService = UnleashServiceProvider.provideUnleashService()
private val inkluderteBehovstyper = if (unleashService.isEnabled(FeatureToggles.InkluderOverstyrteAvklaringsbehov)) {
    setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG, BehovType.OVERSTYR)
} else {
    setOf(BehovType.MANUELT_PÅKREVD, BehovType.MANUELT_FRIVILLIG)
}

val AVKLARINGSBEHOV_FOR_VEILEDER =
    Definisjon.entries
        .filter { it.type in inkluderteBehovstyper }
        .filter { Rolle.SAKSBEHANDLER_OPPFOLGING in it.løsesAv }
        .map { AvklaringsbehovKode(it.kode.name) }
        .toSet()

val AVKLARINGSBEHOV_FOR_SAKSBEHANDLER =
    Definisjon.entries
        .asSequence()
        .filter { it.type in inkluderteBehovstyper }
        .filter { Rolle.SAKSBEHANDLER_NASJONAL in it.løsesAv }
        .filter { it.løsesISteg != StegType.KVALITETSSIKRING }
        .map { AvklaringsbehovKode(it.kode.name) }
        .toSet()

val AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER =
    Definisjon.entries
        .filter { it.type in inkluderteBehovstyper }
        .filter { Rolle.SAKSBEHANDLER_OPPFOLGING in it.løsesAv && Rolle.SAKSBEHANDLER_NASJONAL in it.løsesAv }
        .map { AvklaringsbehovKode(it.kode.name) }
        .toSet()

val AVKLARINGSBEHOV_FOR_BESLUTTER = Definisjon.entries
    .filter { it.type in inkluderteBehovstyper }
    .filter { Rolle.BESLUTTER in it.løsesAv }
    .map { AvklaringsbehovKode(it.kode.name) }
    .toSet()

val AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK =
    PostmottakDefinisjon.entries
        .filter {
            it.type in setOf(
                PostmottakDefinisjon.BehovType.MANUELT_PÅKREVD,
                PostmottakDefinisjon.BehovType.MANUELT_FRIVILLIG
            )
        }
        .filter { Rolle.SAKSBEHANDLER_NASJONAL in it.løsesAv }
        .map { AvklaringsbehovKode(it.kode.name) }.toSet()

val AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK =
    PostmottakDefinisjon.entries
        .filter {
            it.type in setOf(
                PostmottakDefinisjon.BehovType.MANUELT_PÅKREVD,
                PostmottakDefinisjon.BehovType.MANUELT_FRIVILLIG
            )
        }
        .filter { Rolle.SAKSBEHANDLER_OPPFOLGING in it.løsesAv }
        .map { AvklaringsbehovKode(it.kode.name) }.toSet()
