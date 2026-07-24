-- Partial indexes på aktive (ikke-avsluttede) oppgaver.
-- Ekskluderer AVSLUTTET-rader fra indeksen, noe som gir raskere
-- filtrering og sortering i finnOppgaver for alle filterpermutasjoner.

-- Ledige aktive oppgaver: vanligste kall (kunLedigeOppgaver=true)
-- Dekker: avklaringsbehov-filter + ORDER BY behandling_opprettet
CREATE INDEX IDX_OPPGAVE_LEDIG_AVKLARINGSBEHOV_OPPRETTET
    ON OPPGAVE (avklaringsbehov_type, behandling_opprettet)
    WHERE status != 'AVSLUTTET'
      AND reservert_av IS NULL
      AND paa_vent_til IS NULL;

-- Alle aktive oppgaver (kunLedigeOppgaver=false eller null)
CREATE INDEX IDX_OPPGAVE_AKTIV_AVKLARINGSBEHOV_OPPRETTET
    ON OPPGAVE (avklaringsbehov_type, behandling_opprettet)
    WHERE status != 'AVSLUTTET';

-- Behandlingstype-filtrering i kombinasjon med avklaringsbehov
CREATE INDEX IDX_OPPGAVE_AKTIV_BEHANDLINGSTYPE_AVKLARINGSBEHOV
    ON OPPGAVE (behandlingstype, avklaringsbehov_type, behandling_opprettet)
    WHERE status != 'AVSLUTTET';

-- Saksbehandler-filter (RESERVERT_AV IN ...) med sortering
CREATE INDEX IDX_OPPGAVE_AKTIV_RESERVERT_AV_OPPRETTET
    ON OPPGAVE (reservert_av, behandling_opprettet)
    WHERE status != 'AVSLUTTET';

-- Array-overlap-operator (&&) på AARSAKER_TIL_BEHANDLING krever GIN.
-- Partial fordi filteret kun brukes i finnOppgaver (alltid status != 'AVSLUTTET').
CREATE INDEX IDX_OPPGAVE_AARSAKER_GIN
    ON OPPGAVE USING GIN (aarsaker_til_behandling)
    WHERE status != 'AVSLUTTET';

-- IDX_OPPGAVE_RESERVERT_AV erstattes av den nye partial index over
DROP INDEX IDX_OPPGAVE_RESERVERT_AV;
