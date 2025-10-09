package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    VarsleHvisEnhetIkkeGodkjent("VarsleHvisEnhetIkkeGodkjent"),
    NyRutingAvKlageoppgaver("NyRutingAvKlageoppgaver"),
    HentSaksbehandlereForEnhet("HentSaksbehandlereForEnhet");

    override fun key(): String = toggleKey
}
