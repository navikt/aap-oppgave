CREATE TABLE OPPGAVE_HISTORIKK
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    OPPGAVE_ID                  BIGINT                          NOT NULL REFERENCES OPPGAVE (ID),
    STATUS                      VARCHAR(10)                     NOT NULL,
    RESERVERT_AV                VARCHAR(20),
    RESERVERT_TIDSPUNKT         TIMESTAMP(3),
    ENDRET_AV                   VARCHAR(20),
    ENDRET_TIDSPUNKT            TIMESTAMP(3)
);