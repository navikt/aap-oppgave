-- Logisk slette medlemskap og lovvalg filter.
update filter set slettet = false where navn = 'Medlem / Lovvalg';
