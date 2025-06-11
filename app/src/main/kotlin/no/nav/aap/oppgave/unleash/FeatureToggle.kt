package no.nav.aap.oppgave.unleash

interface FeatureToggle {
    fun key(): String
}

enum class FeatureToggles(private val toggleKey: String) : FeatureToggle {
    DummyFeature("DummyFeature"),
    HentVeilederSykefraværsoppfølging("HentVeilederSykefravarsoppfolging"),
    OpprettBrevOppgaver("OpprettBrevOppgaver"),
    UtvidetOppgaveFilter("UtvidetOppgaveFilter");

    override fun key(): String = toggleKey
}
