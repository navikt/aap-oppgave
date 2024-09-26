-- Unique constaint laget i V1.2 virker ikke, så gjør et nytt forsøk med en unique index.
ALTER TABLE OPPGAVE DROP CONSTRAINT UNIQUE_OPPGAVE;

CREATE UNIQUE INDEX OPPGAVE_UNIQUE_INDEX ON OPPGAVE (COALESCE(SAKSNUMMER, ''), COALESCE(BEHANDLING_REF::TEXT, ''), COALESCE(JOURNALPOST_ID::TEXT, ''), AVKLARINGSBEHOV_TYPE);
