package no.nav.aap.oppgave.verdityper

enum class Behandlingstype {
    // Fra behandlingsflyt
    FØRSTEGANGSBEHANDLING,
    REVURDERING,
    TILBAKEKREVING,
    KLAGE,

    // Fra postmottak
    DOKUMENT_HÅNDTERING
}