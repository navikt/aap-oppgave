insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('NAY oppfølgingsoppgave', 'Oppfølgingsoppgaver for NAY', 'Kelvin', current_timestamp),
       ('Kontor oppfølgingsoppgave', 'Oppfølingsoppgaver for Nav-kontor', 'Kelvin', current_timestamp);

insert into filter_behandlingstype (filter_id, behandlingstype)
values ((select id from filter where navn = 'NAY oppfølgingsoppgave'), 'OPPFØLGINGSBEHANDLING'),
       ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), 'OPPFØLGINGSBEHANDLING');

insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype)
values
    -- NAY oppfølgingsoppgave
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '8002'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '8003'),

    -- Kontor oppfølgingsoppgave
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '8001'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '8003');

insert into filter_enhet (filter_id, enhet, filter_modus)
values
    -- NAY oppfølgingsoppgave
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '4491', 'INKLUDER'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '4483', 'INKLUDER'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '4402', 'INKLUDER'),

    -- Kontor oppfølgingsoppgave
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), 'ALLE', 'INKLUDER'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '4491', 'EKSKLUDER'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '4483', 'EKSKLUDER'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '4402', 'EKSKLUDER');