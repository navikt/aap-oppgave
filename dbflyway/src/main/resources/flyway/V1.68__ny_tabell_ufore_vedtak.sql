-- Tabell for å lagre vedtak på en gitt oppgave
CREATE TABLE UFORE_VEDTAK
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    BEHANDLING_REF              UUID                            NOT NULL,
    VIRKNINGSDATO               DATE                            NOT NULL,
    STATUS                      TEXT                            NOT NULL,
    VEDTAK_FJERNET_AV        VARCHAR(20),
    VEDTAK_FJERNET_TIDSPUNKT   TIMESTAMP(3),
    CONSTRAINT UQ_UFORE_VEDTAK
        UNIQUE (BEHANDLING_REF, VIRKNINGSDATO, STATUS)
);

