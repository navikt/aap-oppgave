
CREATE TABLE FILTER
(
    ID                          BIGSERIAL               NOT NULL PRIMARY KEY,
    BESKRIVELSE                 VARCHAR(100)            NOT NULL,
    SLETTET                     BOOLEAN                 NOT NULL DEFAULT FALSE,
    OPPRETTET_AV                VARCHAR(20)             NOT NULL,
    OPPRETTET_TIDSPUNKT         TIMESTAMP(3)            NOT NULL,
    ENDRET_AV                   VARCHAR(20),
    ENDRET_TIDSPUNKT            TIMESTAMP(3)
);

CREATE TABLE FILTER_AVKLARINGSBEHOVTYPE
(
    ID                          BIGSERIAL               NOT NULL PRIMARY KEY,
    FILTER_ID                   BIGINT                  NOT NULL REFERENCES FILTER (ID),
    AVKLARINGSBEHOVTYPE         VARCHAR(4)              NOT NULL
);

CREATE TABLE FILTER_BEHANDLINGSTYPE
(
    ID                          BIGSERIAL               NOT NULL PRIMARY KEY,
    FILTER_ID                   BIGINT                  NOT NULL REFERENCES FILTER (ID),
    BEHANDLINGSTYPE             VARCHAR(40)             NOT NULL
);
