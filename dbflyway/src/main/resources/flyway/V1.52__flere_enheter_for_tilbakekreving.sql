
insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Tilbakekreving'), '4483', 'INKLUDER');
insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Tilbakekreving'), '4402', 'INKLUDER');

insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Beslutter - tilbakekreving'), '4483', 'INKLUDER');
insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Beslutter - tilbakekreving'), '4402', 'INKLUDER');
