-- Logisk slette medlemskap og lovvalg filter.
update filter set slettet = true where navn = 'Medlem / Lovvalg';
