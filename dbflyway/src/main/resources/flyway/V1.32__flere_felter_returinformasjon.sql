ALTER TABLE oppgave
    ADD COLUMN retur_begrunnelse TEXT,
    ADD COLUMN retur_aarsaker    TEXT[],
    ADD COLUMN returnert_av      TEXT;

