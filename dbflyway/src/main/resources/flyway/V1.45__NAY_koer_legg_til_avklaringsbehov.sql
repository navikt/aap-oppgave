-- Legger til nye avklaringsbehov for NAY siden forrige endring
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5040');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '7001');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5024');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5027');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5030');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5096');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5035');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5056');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5020');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5028');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5029');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'NAY saksbehandler'), '5034');


-- Skriv vedtaksbrev skal inn i beslutter-kø for førstegangsbehandling og klage
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Beslutter'), '5051');
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Beslutter - klage'), '5051');

-- Avbryt revurdering skal inn i beslutter-kø
insert into filter_avklaringsbehovtype (filter_id, avklaringsbehovtype) values ( (select id from filter where navn = 'Beslutter'), '5033');

