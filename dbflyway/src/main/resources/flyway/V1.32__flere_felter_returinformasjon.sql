ALTER TABLE oppgave
    ADD COLUMN retur_begrunnelse TEXT,
    ADD COLUMN retur_aarsaker    TEXT[],
    ADD COLUMN retur_returnert_av      TEXT;

