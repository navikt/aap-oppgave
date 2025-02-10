
delete from filter_behandlingstype;
delete from filter_avklaringsbehovtype;
delete from filter;

insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('Førstegangsbehandling kontor', 'Alle kontoroppgaver på førstegangsbehandling utenom kvalitetssikrer.',  'L149075', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('Revurdering kontor', 'Alle kontoroppgaver på revurdering utenom kvalitetssikrer.',  'L149075', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('NAY saksbehandler', 'Alle NAY oppgaver utenom beslutter, skrive brev, lovvalg og medlemskap.',  'L149075', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('Post', 'Journalføring og dokumentbehandling.',  'L149075', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('Medlem / Lovvalg', 'Medlemskap og lovvalg.',  'L149075', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('Kvalitetssikrer', 'Kvalitetssikringsoppgaver.',  'L149075', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('Beslutter', 'Beslutte vedtak og skrive brev.',  'L149075', current_timestamp);
insert into filter (navn, beskrivelse, opprettet_av, opprettet_tidspunkt) values ('Alle oppgaver', 'Alle oppgave.',  'L149075', current_timestamp);


insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Førstegangsbehandling kontor'), 'FØRSTEGANGSBEHANDLING');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Førstegangsbehandling kontor'), '5003');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Førstegangsbehandling kontor'), '5004');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Førstegangsbehandling kontor'), '5005');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Førstegangsbehandling kontor'), '5006');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Førstegangsbehandling kontor'), '5015');

insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Revurdering kontor'), 'REVURDERING');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Revurdering kontor'), '5003');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Revurdering kontor'), '5004');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Revurdering kontor'), '5005');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Revurdering kontor'), '5006');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Revurdering kontor'), '5015');

insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'NAY saksbehandler'), 'FØRSTEGANGSBEHANDLING');
insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'NAY saksbehandler'), 'REVURDERING');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5001');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5008');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5014');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5007');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5013');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5009');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5010');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5011');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5012');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5017');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5098');

insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Post'), 'DOKUMENT_HÅNDTERING');
insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Post'), 'JOURNALFØRING');

insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Medlem / Lovvalg'), 'FØRSTEGANGSBEHANDLING');
insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Medlem / Lovvalg'), 'REVURDERING');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Medlem / Lovvalg'), '5017');

insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Kvalitetssikrer'), 'FØRSTEGANGSBEHANDLING');
insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Kvalitetssikrer'), 'REVURDERING');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Kvalitetssikrer'), '5097');

insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Beslutter'), 'FØRSTEGANGSBEHANDLING');
insert into filter_behandlingstype (filter_id, behandlingstype) values ( (select id from filter where navn = 'Beslutter'), 'REVURDERING');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Beslutter'), '5099');
