ALTER TABLE OPPGAVE
    ADD COLUMN FORESPORSEL_SENDT_TIL_BEHANDLER BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE mottatt_dokument ADD COLUMN mottatt_tidspunkt TIMESTAMP(3);
UPDATE mottatt_dokument SET mottatt_tidspunkt = opprettet_tidspunkt;
ALTER TABLE mottatt_dokument ALTER COLUMN mottatt_tidspunkt SET NOT NULL;