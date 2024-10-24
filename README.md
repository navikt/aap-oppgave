# aap-oppgave

Oppgave-håndtering for Arbeidsavklaringspenger (AAP).
Holder tilstand på oppgaver oppstått i behandlingsflyt og postmottak-backend.
Inneholder følgende funksjonalitet:
* Opprett ny oppgave
* Plukk og reserver neste oppgave for innlogget saksbehandler gitt et filter.
* Hent mine reserverte oppgaver for innlogget saksbehandler
* Avslutt en oppgave knyttet til en behandling eller journalpost.
* Hent tilgjengelige filter.
* (todo: vedlikehold av filter)

### API-dokumentasjon

APIene er dokumentert med Swagger: http://localhost:8080/swagger-ui/index.html

### Lokalt utviklingsmiljø:

Applikasjonen aap-oppgave bruker test-containers for integrasjonstest med databasen.
En Docker-container er derfor nødvendig.
For macOS og Linux anbefaler vi Colima. Det kan være nødvendig med et par tilpasninger:

* `export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=$HOME/.colima/docker.sock`
* `export DOCKER_HOST=unix://$TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
* `export TESTCONTAINERS_RYUK_DISABLED=true`