alter table jobb add column prioritet smallint not null default 100;
alter table jobb add column kjorbar boolean not null default false;

create index idx_jobb_plukkbar_prioritet on jobb (prioritet, neste_kjoring)
    where status = 'KLAR' and kjorbar;