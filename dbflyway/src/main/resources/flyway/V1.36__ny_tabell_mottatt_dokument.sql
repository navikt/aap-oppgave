-- Tabell for å holde oversikt over dokumenter
CREATE TABLE MOTTATT_DOKUMENT
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    TYPE                        TEXT                            NOT NULL,
    BEHANDLING_REF              UUID                            NOT NULL,
    REFERANSE                   TEXT                            NOT NULL UNIQUE,
    OPPRETTET_AV                VARCHAR(20)                     NOT NULL,
    OPPRETTET_TIDSPUNKT         TIMESTAMP(3)                    NOT NULL,
    REGISTRERT_LEST_AV          VARCHAR(20),
    REGISTRERT_LEST_TIDSPUNKT   TIMESTAMP(3)
);

ALTER TABLE oppgave ADD COLUMN uleste_dokumenter BOOLEAN NOT NULL DEFAULT FALSE;
