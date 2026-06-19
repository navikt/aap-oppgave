package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    VarsleOmOppgaverEldreEnn7Dager("VarsleOmOppgaverEldreEnn7Dager"),
    SoningHastemarkering("SoningHastemarkering"),
    SorterOppgavelistePaBelop("SorterOppgavelistePaBelop");

    override fun key(): String = toggleKey
}
