package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    LagreMottattDokumenter("LagreMottattDokumenter"),
    HentIdenterFraBehandlingsflyt("HentIdenterFraBehandlingsflyt");

    override fun key(): String = toggleKey
}
