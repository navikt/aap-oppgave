insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('Klagebehandling kontor', 'Behandle klage - Nav-kontor', 'Kelvin', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('NAY klagebehandling', 'Behandle klage - NAY', 'Kelvin', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt)
values ('Beslutter - klage', 'Beslutte klage', 'Kelvin', current_timestamp);

insert into filter_behandlingstype (filter_id, behandlingstype)
values ((select id from filter where navn = 'Klagebehandling kontor'), 'KLAGE'),
       ((select id from filter where navn = 'Kvalitetssikrer'), 'KLAGE'),
       ((select id from filter where navn = 'Beslutter - klage'), 'KLAGE'),
       ((select id from filter where navn = 'NAY klagebehandling'), 'SVAR_FRA_ANDREINSTANS'),
       ((select id from filter where navn = 'NAY klagebehandling'), 'KLAGE');

insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype)
values ((select id from filter where navn = 'Klagebehandling kontor'), '6002'),
       ((select id from filter where navn = 'Klagebehandling kontor'), '6010'),
       ((select id from filter where navn = 'NAY klagebehandling'), '5999'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6000'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6001'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6003'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6004'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6005'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6006'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6007'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6008'),
       ((select id from filter where navn = 'NAY klagebehandling'), '6010'),
       ((select id from filter where navn = 'Beslutter - klage'), '5099');

insert into filter_enhet (filter_id, enhet, filter_modus)
values ((select id from filter where navn = 'Klagebehandling kontor'), 'ALLE', 'INKLUDER'),
       ((select id from filter where navn = 'Klagebehandling kontor'), '4491', 'EKSKLUDER'),
       ((select id from filter where navn = 'Klagebehandling kontor'), '4483', 'EKSKLUDER'),
       ((select id from filter where navn = 'Klagebehandling kontor'), '4402', 'EKSKLUDER'),
       ((select id from filter where navn = 'NAY klagebehandling'), '2103', 'INKLUDER'),
       ((select id from filter where navn = 'NAY klagebehandling'), '4491', 'INKLUDER'),
       ((select id from filter where navn = 'NAY klagebehandling'), '4483', 'INKLUDER'),
       ((select id from filter where navn = 'NAY klagebehandling'), '4402', 'INKLUDER'),
       ((select id from filter where navn = 'Førstegangsbehandling kontor'), '4483', 'EKSKLUDER'),
       ((select id from filter where navn = 'Førstegangsbehandling kontor'), '4402', 'EKSKLUDER'),
       ((select id from filter where navn = 'Revurdering kontor'), '4483', 'EKSKLUDER'),
       ((select id from filter where navn = 'Revurdering kontor'), '4402', 'EKSKLUDER'),
       ((select id from filter where navn = 'Kvalitetssikrer'), '4483', 'EKSKLUDER'),
       ((select id from filter where navn = 'Kvalitetssikrer'), '4402', 'EKSKLUDER'),
       ((select id from filter where navn = 'NAY saksbehandler'), '4483', 'INKLUDER'),
       ((select id from filter where navn = 'NAY saksbehandler'), '4402', 'INKLUDER'),
       ((select id from filter where navn = 'Medlem / Lovvalg'), '4483', 'INKLUDER'),
       ((select id from filter where navn = 'Medlem / Lovvalg'), '4402', 'INKLUDER'),
       ((select id from filter where navn = 'Beslutter'), '4483', 'INKLUDER'),
       ((select id from filter where navn = 'Beslutter'), '4402', 'INKLUDER'),
       ((select id from filter where navn = 'Beslutter - klage'), '2103', 'INKLUDER'),
       ((select id from filter where navn = 'Beslutter - klage'), '4491', 'INKLUDER'),
       ((select id from filter where navn = 'Beslutter - klage'), '4483', 'INKLUDER'),
       ((select id from filter where navn = 'Beslutter - klage'), '4402', 'INKLUDER');
       
    


