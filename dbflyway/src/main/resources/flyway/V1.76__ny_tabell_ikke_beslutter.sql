-- Identer som ikke kan beslutte vedtaket på behandlingen, slik behandlingsflyt har sendt dem.
-- Tom liste betyr at ingen er utelukket. Mangler raden er det ukjent (eldre hendelser).
CREATE TABLE IKKE_BESLUTTER
(
    BEHANDLING_REF      UUID PRIMARY KEY,
    IDENTER             TEXT[]       NOT NULL,
    OPPRETTET_TIDSPUNKT TIMESTAMP(3) NOT NULL DEFAULT current_timestamp,
    ENDRET_TIDSPUNKT    TIMESTAMP(3) NOT NULL DEFAULT current_timestamp
);
