package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    VarsleOmOppgaverEldreEnn7Dager("VarsleOmOppgaverEldreEnn7Dager"),
    FiltrereInnadOppgaveKo("FiltrereInnadOppgaveKo");

    override fun key(): String = toggleKey
}
