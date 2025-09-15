package no.nav.aap.oppgave.verdityper

enum class Behandlingstype {
    // Fra behandlingsflyt
    FØRSTEGANGSBEHANDLING,
    REVURDERING,
    TILBAKEKREVING,
    KLAGE,
    SVAR_FRA_ANDREINSTANS,
    OPPFØLGINGSBEHANDLING,
    AKTIVITETSPLIKT,
    AKTIVITETSPLIKT_11_9,

    // Fra postmottak
    DOKUMENT_HÅNDTERING,
    JOURNALFØRING
}