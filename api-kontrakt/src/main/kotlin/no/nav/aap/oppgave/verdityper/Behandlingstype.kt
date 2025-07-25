package no.nav.aap.oppgave.verdityper

enum class Behandlingstype {
    // Fra behandlingsflyt
    FØRSTEGANGSBEHANDLING,
    REVURDERING,
    TILBAKEKREVING,
    KLAGE,
    SVAR_FRA_ANDREINSTANS,
    OPPFØLGINGSBEHANDLING,

    // Fra postmottak
    DOKUMENT_HÅNDTERING,
    JOURNALFØRING
}