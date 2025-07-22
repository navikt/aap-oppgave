-- Tabell for å lagre markeringer på en gitt oppgave
CREATE TABLE MARKERING
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    BEHANDLING_REF              UUID                            NOT NULL,
    MARKERING_TYPE              TEXT                            NOT NULL,
    BEGRUNNELSE                 TEXT                            NOT NULL,
    OPPRETTET_AV                TEXT DEFAULT 'Ukjent'           NOT NULL
);


