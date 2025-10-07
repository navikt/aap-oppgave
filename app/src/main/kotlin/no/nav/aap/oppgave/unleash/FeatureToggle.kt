package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    VarsleHvisEnhetIkkeGodkjent("VarsleHvisEnhetIkkeGodkjent"),
    NyRutingAvKlageoppgaver("NyRutingAvKlageoppgaver");

    override fun key(): String = toggleKey
}
