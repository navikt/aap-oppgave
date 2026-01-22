package no.nav.aap.oppgave.liste

enum class OppgaveSorteringFelt {
    PERSONIDENT,
    BEHANDLINGSTYPE,
    BEHANDLING_OPPRETTET,
    ÅRSAK_TIL_OPPRETTELSE,
    AVKLARINGSBEHOV_KODE,
    OPPRETTET_TIDSPUNKT,
}

enum class OppgaveSorteringRekkefølge {
    ASC,
    DESC,
}
data class OppgaveSortering (
    val sortBy: OppgaveSorteringFelt? = null,
    val sortOrder: OppgaveSorteringRekkefølge? = null
)
