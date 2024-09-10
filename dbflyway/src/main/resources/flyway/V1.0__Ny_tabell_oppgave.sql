CREATE TABLE OPPGAVE
(
    ID                          BIGSERIAL                       NOT NULL PRIMARY KEY,
    SAKSNUMMER                  VARCHAR(19)                     NOT NULL,
    BEHANDLING_REF              UUID                            NOT NULL,
    BEHANDLING_OPPRETTET        TIMESTAMP(3)                    NOT NULL,
    AVKLARINGSBEHOV_KODE        VARCHAR(4)                      NOT NULL,
    OPPGAVE_STATUS              VARCHAR(10)                     NOT NULL,
    RESERVERT_AV                VARCHAR(20),
    RESERVERT_TIDSPUNKT         TIMESTAMP(3),
    OPPRETTET_AV                VARCHAR(20)                     NOT NULL,
    OPPRETTET_TIDSPUNKT         TIMESTAMP(3)                    NOT NULL,
    ENDRET_AV                   VARCHAR(20),
    ENDRET_TIDSPUNKT            TIMESTAMP(3)
);