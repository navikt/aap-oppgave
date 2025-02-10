
delete from filter_avklaringsbehovtype where filter_id in (select id from filter where navn = 'NAY saksbehandler') and avklaringsbehovtype = '5017';