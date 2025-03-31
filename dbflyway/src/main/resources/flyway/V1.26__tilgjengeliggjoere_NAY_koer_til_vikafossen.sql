insert into filter_enhet (filter_id, enhet, filter_modus)
values
    ((select id from filter where navn = 'NAY saksbehandler'), '2103', 'INKLUDER'),
    ((select id from filter where navn = 'Medlem / Lovvalg'), '2103', 'INKLUDER'),
    ((select id from filter where navn = 'Beslutter'), '2103', 'INKLUDER');

