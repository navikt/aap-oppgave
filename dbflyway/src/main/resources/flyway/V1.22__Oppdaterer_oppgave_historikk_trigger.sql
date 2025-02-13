CREATE OR REPLACE FUNCTION oppdater_oppgave_historikk()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO OPPGAVE_HISTORIKK (
        OPPGAVE_ID, STATUS,
        RESERVERT_AV,
        RESERVERT_TIDSPUNKT,
        ENHET,
        OPPFOLGINGSENHET,
        PAA_VENT_TIL,
        PAA_VENT_AARSAK,
        ENDRET_AV,
        ENDRET_TIDSPUNKT,
        VEILEDER
    )
    VALUES (
               NEW.id,
               NEW.STATUS,
               NEW.RESERVERT_AV,
               NEW.RESERVERT_TIDSPUNKT,
               NEW.ENHET,
               NEW.OPPFOLGINGSENHET,
               NEW.PAA_VENT_TIL,
               NEW.PAA_VENT_AARSAK,
               NEW.ENDRET_AV,
               NEW.ENDRET_TIDSPUNKT,
               NEW.VEILEDER);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;