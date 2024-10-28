CREATE OR REPLACE FUNCTION oppdater_oppgave_historikk()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO OPPGAVE_HISTORIKK (OPPGAVE_ID, STATUS, RESERVERT_AV, RESERVERT_TIDSPUNKT, ENDRET_AV, ENDRET_TIDSPUNKT)
    VALUES (NEW.id, NEW.STATUS, NEW.RESERVERT_AV, NEW. RESERVERT_TIDSPUNKT, NEW.ENDRET_AV, NEW.ENDRET_TIDSPUNKT);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER oppgave_opprettet_trigger
    AFTER INSERT ON oppgave
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppgave_historikk();

CREATE TRIGGER oppgave_endring_trigger
    AFTER UPDATE ON oppgave
    FOR EACH ROW
EXECUTE FUNCTION oppdater_oppgave_historikk();