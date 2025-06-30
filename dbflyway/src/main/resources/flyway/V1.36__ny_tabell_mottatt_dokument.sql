-- Tabell for å holde oversikt over dokumenter (I første omgang legeerklæringer) som saksbehandler kvitter ut at de har sett
CREATE TABLE MOTTATT_DOKUMENT
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    TYPE                        TEXT                            NOT NULL,
    BEHANDLING_REF              UUID                            NOT NULL,
    REFERANSE                   TEXT                            NOT NULL UNIQUE,
    OPPRETTET_AV                VARCHAR(20)                     NOT NULL,
    OPPRETTET_TIDSPUNKT         TIMESTAMP(3)                    NOT NULL,
    KVITTERT_AV                 VARCHAR(20),
    KVITTERT_TIDSPUNKT          TIMESTAMP(3)
);