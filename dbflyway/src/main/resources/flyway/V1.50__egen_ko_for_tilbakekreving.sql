insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('Tilbakekreving', 'Tilbakekreving.',  'Kelvin', current_timestamp);

insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Tilbakekreving'), '4491', 'INKLUDER');
insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Tilbakekreving'), '2103', 'INKLUDER');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype)
values ((select id from filter where navn = 'Tilbakekreving'), '9082');
insert into filter_behandlingstype (filter_id, behandlingstype)
values ((select id from filter where navn = 'Tilbakekreving'), 'TILBAKEKREVING');

insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('Beslutter - tilbakekreving', 'Beslutter - tilbakekreving.',  'Kelvin', current_timestamp);

insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Beslutter - tilbakekreving'), '4491', 'INKLUDER');
insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Beslutter - tilbakekreving'), '2103', 'INKLUDER');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype)
values ((select id from filter where navn = 'Beslutter - tilbakekreving'), '9083');
insert into filter_behandlingstype (filter_id, behandlingstype)
values ((select id from filter where navn = 'Beslutter - tilbakekreving'), 'TILBAKEKREVING');
