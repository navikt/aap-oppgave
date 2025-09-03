package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    VarsleHvisEnhetIkkeGodkjent("VarsleHvisEnhetIkkeGodkjent");
    HentIdenterFraBehandlingsflyt("HentIdenterFraBehandlingsflyt"),
    HentSaksbehandlerNavnFraNom("HentSaksbehandlerNavnFraNom");

    override fun key(): String = toggleKey
}
