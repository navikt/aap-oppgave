package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    VarsleOmOppgaverEldreEnn7Dager("VarsleOmOppgaverEldreEnn7Dager"),
    SetterIkkeReserverTilPaaOppdatering("SetterIkkeReserverTilPaaOppdatering");

    override fun key(): String = toggleKey
}
