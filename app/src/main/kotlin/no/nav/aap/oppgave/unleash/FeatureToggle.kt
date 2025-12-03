package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    AvsluttOppgaverVedGjenaapning("AvsluttOppgaverVedGjenaapning"),
    VarsleOmOppgaverEldreEnn7Dager("VarsleOmOppgaverEldreEnn7Dager");

    override fun key(): String = toggleKey
}
