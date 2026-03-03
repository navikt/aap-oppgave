insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('NAY oppfølgingsoppgave', 'Oppfølgingsoppgaver for NAY', 'Kelvin', current_timestamp),
       ('Kontor oppfølgingsoppgave', 'Oppfølingsoppgaver for Nav-kontor', 'Kelvin', current_timestamp);

insert into filter_behandlingstype (filter_id, behandlingstype)
values ((select id from filter where navn = 'NAY oppfølgingsoppgave'), 'OPPFØLGINGSBEHANDLING'),
       ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), 'OPPFØLGINGSBEHANDLING');

insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype)
values
    -- NAY oppfølgingsoppgave
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5001'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5008'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5014'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5007'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5013'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5009'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5010'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5011'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5012'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5098'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5040'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '7001'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5024'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5027'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5030'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5096'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5035'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5056'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5020'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5028'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5029'),
    ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '5034'),

    -- Kontor oppfølgingsoppgave
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '5003'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '5004'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '5005'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '5006'),
    ((select id from filter where navn = 'Kontor oppfølgingsoppgave'), '5015');


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