package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    VarsleHvisEnhetIkkeGodkjent("VarsleHvisEnhetIkkeGodkjent"),
    NyRutingAvKlageoppgaver("NyRutingAvKlageoppgaver"),
    ToTrinnForAndreGang("ToTrinnForAndreGang"),
    OverstyrTilNavKontor("OverstyrTilNavKontor"),
    AvsluttOppgaverVedGjenaapning("AvsluttOppgaverVedGjenaapning");

    override fun key(): String = toggleKey
}
