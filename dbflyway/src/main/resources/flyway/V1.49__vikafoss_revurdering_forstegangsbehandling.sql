insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'NAY førstegangsbehandling'), '2103', 'INKLUDER'),
       ((select id from filter where navn = 'NAY revurdering'), '2103', 'INKLUDER'),
       ((select id from filter where navn = 'NAY oppfølgingsoppgave'), '2103', 'INKLUDER');