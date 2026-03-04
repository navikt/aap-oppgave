insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('NAY førstegangsbehandling', 'Alle NAY-oppgaver på førstegangsbehandling.', 'Kelvin', current_timestamp),
       ('NAY revurdering', 'Alle NAY-oppgaver på revurdering.', 'Kelvin', current_timestamp);

insert into filter_behandlingstype (filter_id, behandlingstype)
values ((select id from filter where navn = 'NAY førstegangsbehandling'), 'FØRSTEGANGSBEHANDLING'),
       ((select id from filter where navn = 'NAY revurdering'), 'REVURDERING');

insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype)
values
    -- førstegangsbehandling
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5001'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5008'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5014'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5007'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5013'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5009'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5010'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5011'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5012'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5098'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5040'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '7001'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5024'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5027'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5030'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5096'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5035'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5056'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5020'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5028'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5029'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '5034'),

    -- revurdering
    ((select id from filter where navn = 'NAY revurdering'), '5001'),
    ((select id from filter where navn = 'NAY revurdering'), '5008'),
    ((select id from filter where navn = 'NAY revurdering'), '5014'),
    ((select id from filter where navn = 'NAY revurdering'), '5007'),
    ((select id from filter where navn = 'NAY revurdering'), '5013'),
    ((select id from filter where navn = 'NAY revurdering'), '5009'),
    ((select id from filter where navn = 'NAY revurdering'), '5010'),
    ((select id from filter where navn = 'NAY revurdering'), '5011'),
    ((select id from filter where navn = 'NAY revurdering'), '5012'),
    ((select id from filter where navn = 'NAY revurdering'), '5098'),
    ((select id from filter where navn = 'NAY revurdering'), '5040'),
    ((select id from filter where navn = 'NAY revurdering'), '7001'),
    ((select id from filter where navn = 'NAY revurdering'), '5024'),
    ((select id from filter where navn = 'NAY revurdering'), '5027'),
    ((select id from filter where navn = 'NAY revurdering'), '5030'),
    ((select id from filter where navn = 'NAY revurdering'), '5096'),
    ((select id from filter where navn = 'NAY revurdering'), '5035'),
    ((select id from filter where navn = 'NAY revurdering'), '5056'),
    ((select id from filter where navn = 'NAY revurdering'), '5020'),
    ((select id from filter where navn = 'NAY revurdering'), '5028'),
    ((select id from filter where navn = 'NAY revurdering'), '5029'),
    ((select id from filter where navn = 'NAY revurdering'), '5034');

insert into filter_enhet (filter_id, enhet, filter_modus)
values
    -- førstegangsbehandling
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '4491', 'INKLUDER'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '4483', 'INKLUDER'),
    ((select id from filter where navn = 'NAY førstegangsbehandling'), '4402', 'INKLUDER'),

    -- revurdering
    ((select id from filter where navn = 'NAY revurdering'), '4491', 'INKLUDER'),
    ((select id from filter where navn = 'NAY revurdering'), '4483', 'INKLUDER'),
    ((select id from filter where navn = 'NAY revurdering'), '4402', 'INKLUDER');