-- Tabell for å lagre vedtak på en gitt oppgave
CREATE TABLE UFORE_VEDTAK
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    BEHANDLING_REF              UUID                            NOT NULL,
    VIRKNINGSDATO               DATE                            NOT NULL,
    STATUS                      TEXT                            NOT NULL
);

