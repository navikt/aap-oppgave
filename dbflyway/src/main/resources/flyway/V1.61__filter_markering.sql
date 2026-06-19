create table filter_markering
(
    ID          BIGSERIAL NOT NULL PRIMARY KEY,
    FILTER_ID   BIGINT    NOT NULL REFERENCES FILTER (ID),
    TYPE        TEXT      NOT NULL,
    FILTERMODUS TEXT      NOT NULL DEFAULT 'INKLUDER'
);

CREATE UNIQUE INDEX filter_markering_filter_id_type_uindex
    ON filter_markering (filter_id, type);