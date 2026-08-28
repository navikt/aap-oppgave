-- Tabell for å lagre vedtak på en gitt oppgave
CREATE TABLE UFORE_VEDTAK
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    BEHANDLING_REF              UUID                            NOT NULL,
    VIRKNINGSDATO               TIMESTAMPTZ DEFAULT NOW()       NOT NULL,
    STATUS                      TEXT                            NOT NULL
);

