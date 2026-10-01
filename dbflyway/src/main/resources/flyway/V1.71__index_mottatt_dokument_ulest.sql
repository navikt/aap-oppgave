CREATE INDEX idx_mottatt_dokument_ulest_type
ON mottatt_dokument (behandling_ref, mottatt_tidspunkt DESC)
INCLUDE (type)
WHERE registrert_lest_av IS NULL;

