package no.nav.aap.oppgave.verdityper

enum class Behandlingstype(val fraBehandlingsflyt: Boolean) {
    // Fra behandlingsflyt
    FØRSTEGANGSBEHANDLING(true),
    REVURDERING(true),
    TILBAKEKREVING(true),
    KLAGE(true),
    SVAR_FRA_ANDREINSTANS(true),
    OPPFØLGINGSBEHANDLING(true),
    AKTIVITETSPLIKT(true),
    AKTIVITETSPLIKT_11_9(true),

    // Fra postmottak
    DOKUMENT_HÅNDTERING(false),
    JOURNALFØRING(false)
}