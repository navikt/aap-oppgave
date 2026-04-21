CREATE OR REPLACE VIEW siste_avsluttede_oppgave AS
select distinct on (behandling_ref) behandling_ref as behandling_ref_forrige_oppgave, enhet as enhet_forrige_oppgave
from oppgave
where status = 'AVSLUTTET'
order by behandling_ref, endret_tidspunkt desc
;

