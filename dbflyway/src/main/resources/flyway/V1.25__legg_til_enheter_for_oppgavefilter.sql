create table filter_enhet
(
    id        bigserial not null primary key,
    filter_id bigint    not null references filter (id),
    enhet     varchar(255) not null default 'ALLE',
    filter_modus varchar(10) default 'INKLUDER'
);

create unique index filter_enheter_filter_id_enhet_uindex
    on filter_enhet (filter_id, enhet);

insert into filter_enhet (filter_id, enhet, filter_modus) 
values 
       ((select id from filter where navn = 'Førstegangsbehandling kontor'), 'ALLE', 'INKLUDER'),
       ((select id from filter where navn = 'Førstegangsbehandling kontor'), '4491', 'EKSKLUDER'),
       ((select id from filter where navn = 'Revurdering kontor'), 'ALLE', 'INKLUDER'),
       ((select id from filter where navn = 'Revurdering kontor'), '4491', 'EKSKLUDER'),
       ((select id from filter where navn = 'NAY saksbehandler'), '4491', 'INKLUDER'),
       ((select id from filter where navn = 'Post'), 'ALLE', 'INKLUDER'),
       ((select id from filter where navn = 'Medlem / Lovvalg'), '4491', 'INKLUDER'),
       ((select id from filter where navn = 'Kvalitetssikrer'), 'ALLE', 'INKLUDER'),
       ((select id from filter where navn = 'Kvalitetssikrer'), '4491', 'EKSKLUDER'),
       ((select id from filter where navn = 'Beslutter'), '4491', 'INKLUDER'),
       ((select id from filter where navn = 'Alle oppgaver'), 'ALLE', 'INKLUDER');


