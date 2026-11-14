-- Gjenstående sykedager/maksdato per behandling, lagret slik det er mottatt fra behandlingsflyt.
-- NULL i GJENSTAENDE_DAGER, VURDERT_TIL_OG_MED og MAKSDATO betyr ukjent, ikke 0.
CREATE TABLE GJENSTAENDE_SYKEDAGER
(
    BEHANDLING_REF      UUID PRIMARY KEY,
    GRUNNLAG            TEXT         NOT NULL,
    GJENSTAENDE_DAGER   INT,
    VURDERT_TIL_OG_MED  DATE,
    MAKSDATO            DATE,
    OPPRETTET_TIDSPUNKT TIMESTAMP(3) NOT NULL DEFAULT current_timestamp,
    ENDRET_TIDSPUNKT    TIMESTAMP(3) NOT NULL DEFAULT current_timestamp
);
