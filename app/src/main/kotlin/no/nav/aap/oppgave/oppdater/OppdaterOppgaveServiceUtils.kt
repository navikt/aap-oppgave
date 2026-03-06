package no.nav.aap.oppgave.oppdater

import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_BESLUTTER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER
import no.nav.aap.oppgave.AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK
import no.nav.aap.oppgave.AvklaringsbehovKode
import no.nav.aap.oppgave.OppgaveDto
import no.nav.aap.oppgave.ReturInformasjon
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovHendelse
import no.nav.aap.oppgave.oppdater.hendelse.AvklaringsbehovStatus
import no.nav.aap.oppgave.oppdater.hendelse.KELVIN
import no.nav.aap.oppgave.oppdater.hendelse.OppgaveOppdatering
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger(OppdaterOppgaveService::class.java)
internal fun OppgaveOppdatering.hvemLøsteForrigeAvklaringsbehov(): Pair<AvklaringsbehovKode, String>? {
    val sisteAvsluttetAvklaringsbehov = avklaringsbehov
        .filter { it.status == AvklaringsbehovStatus.AVSLUTTET }
        .maxByOrNull { it.sistEndret() }

    if (sisteAvsluttetAvklaringsbehov == null) {
        val beskrivelse = avklaringsbehov.joinToString { beh ->
            val siste = beh.endringer.maxByOrNull { it.tidsstempel }
            "${beh.avklaringsbehovKode.kode}:${beh.status} (sistEndret=${siste?.tidsstempel}, av=${siste?.endretAv})"
        }
        log.info("Fant ingen avsluttede avklaringsbehov. Behovene: [$beskrivelse], saksnummer: ${this.saksnummer}")
        return null
    }

    if (sisteAvsluttetAvklaringsbehov.sistEndretAv() == KELVIN) {
        log.info("Siste avsluttede avklaringsbehov ble løst av systembruker $KELVIN, oppgave vil ikke bli reservert.")
    }

    return Pair(sisteAvsluttetAvklaringsbehov.avklaringsbehovKode, sisteAvsluttetAvklaringsbehov.sistEndretAv())
}

internal fun sammeSaksbehandlerType(
    avklaringsbehovKode1: AvklaringsbehovKode,
    avklaringsbehovKode2: AvklaringsbehovKode
): Boolean {
    return when (avklaringsbehovKode1) {
        in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER -> true
        in AVKLARINGSBEHOV_FOR_VEILEDER if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER -> true
        in AVKLARINGSBEHOV_FOR_BESLUTTER if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_BESLUTTER -> true
        in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_SAKSBEHANDLER_POSTMOTTAK -> true
        in AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK if avklaringsbehovKode2 in AVKLARINGSBEHOV_FOR_VEILEDER_POSTMOTTAK -> true
        else -> false
    }
}

internal fun OppgaveOppdatering.opprettNyOppgave(
    personIdent: String?,
    avklaringsbehovKode: AvklaringsbehovKode,
    behandlingstype: Behandlingstype,
    ident: String,
    enhet: String,
    oppfølgingsenhet: String?,
    veilederArbeid: String?,
    veilederSykdom: String?,
    påVentTil: LocalDate?,
    påVentÅrsak: String?,
    venteBegrunnelse: String?,
    vurderingsbehov: List<String>,
    årsakTilOpprettelse: String?,
    harFortroligAdresse: Boolean,
    erSkjermet: Boolean,
    harUlesteDokumenter: Boolean,
    returInformasjon: ReturInformasjon?,
    utløptVentefrist: LocalDate? = null,
    saksnummer: String? = null,
): OppgaveDto {
    return OppgaveDto(
        personIdent = personIdent,
        saksnummer = saksnummer,
        behandlingRef = this.referanse,
        journalpostId = this.journalpostId,
        enhet = enhet,
        oppfølgingsenhet = oppfølgingsenhet,
        veilederArbeid = veilederArbeid,
        veilederSykdom = veilederSykdom,
        behandlingOpprettet = this.opprettetTidspunkt,
        avklaringsbehovKode = avklaringsbehovKode.kode,
        behandlingstype = behandlingstype,
        opprettetAv = ident,
        opprettetTidspunkt = LocalDateTime.now(),
        påVentTil = påVentTil,
        påVentÅrsak = påVentÅrsak,
        venteBegrunnelse = venteBegrunnelse,
        årsakerTilBehandling = vurderingsbehov,
        vurderingsbehov = vurderingsbehov,
        årsakTilOpprettelse = årsakTilOpprettelse,
        harFortroligAdresse = harFortroligAdresse,
        erSkjermet = erSkjermet,
        returStatus = returInformasjon?.status,
        returInformasjon = returInformasjon?.let {
            ReturInformasjon(
                status = it.status,
                årsaker = it.årsaker,
                begrunnelse = it.begrunnelse,
                endretAv = it.endretAv
            )
        },
        utløptVentefrist = utløptVentefrist,
        harUlesteDokumenter = harUlesteDokumenter
    )
}

internal fun utledUtløptVentefrist(
    oppgaveOppdatering: OppgaveOppdatering,
    eksisterendeOppgave: OppgaveDto,
): LocalDate? {
    return if (oppgaveTattAvVentAutomatiskIDenneOppdateringen(oppgaveOppdatering, eksisterendeOppgave)) {
        // behandling er nettopp tatt av vent, lagre ned nylig utløpt ventefrist
        eksisterendeOppgave.påVentTil
    } else if (eksisterendeOppgave.utløptVentefrist != null && oppgaveOppdatering.venteInformasjon?.frist == null) {
        // oppgaven har allerede en utløptVentefrist og er ikke satt på vent på nytt i denne oppdateringen. Viderefører den forrige.
        eksisterendeOppgave.utløptVentefrist
    } else {
        null
    }
}

internal fun List<AvklaringsbehovHendelse>.kelvinTokBehandlingAvVent(): Boolean {
    val sisteLukkedeVentebehov =
        this.filter { !it.status.erÅpent() }.maxByOrNull { ventebehov -> ventebehov.endringer.maxOf { it.tidsstempel } }
    if (sisteLukkedeVentebehov == null) {
        return false
    }

    // Endringen som lukket ventebehovet er gjort av Kelvin
    val sisteVentebehovLukketAvKelvin =
        sisteLukkedeVentebehov.endringer.maxByOrNull { it.tidsstempel }?.endretAv.equals(KELVIN, ignoreCase = true)

    // På siste endring der frist var satt, var frist i dag.
    val ventebehovHaddeFristIDag =
        sisteLukkedeVentebehov.endringer
            .filter { it.påVentTil?.isEqual(LocalDate.now()) == true }
            .maxByOrNull { it.tidsstempel } == sisteLukkedeVentebehov.endringer.filter { it.påVentTil != null }
            .maxByOrNull { it.tidsstempel }

    return sisteVentebehovLukketAvKelvin && ventebehovHaddeFristIDag
}

internal fun skalOverstyresTilLokalKontor(
    oppgaveOppdatering: OppgaveOppdatering,
    avklaringsbehovHendelse: AvklaringsbehovHendelse
): Boolean {
    // Hvis avklaringsbehov kan løses av begge roller, skal oppgaven gå til lokalkontor dersom forrige oppgave på behandling var på lokalkontor
    // P.t. gjelder dette trekk søknad og trekk klage
    val avklaringsbehovForBeggeRoller = AVKLARINGSBEHOV_FOR_VEILEDER_OG_SAKSBEHANDLER.map { it.kode }
    if (avklaringsbehovHendelse.avklaringsbehovKode.kode !in avklaringsbehovForBeggeRoller) {
        return false
    }
    val varForrigeOppgaveHosNavKontor = oppgaveOppdatering.avklaringsbehov
        .filter { avklaringsbehov -> avklaringsbehov.avklaringsbehovKode.kode !in avklaringsbehovForBeggeRoller }
        .maxByOrNull { it.sistEndret() }?.avklaringsbehovKode?.kode in AVKLARINGSBEHOV_FOR_VEILEDER.map { it.kode }
    return varForrigeOppgaveHosNavKontor
}

private fun oppgaveTattAvVentAutomatiskIDenneOppdateringen(
    oppgaveOppdatering: OppgaveOppdatering,
    eksisterendeOppgave: OppgaveDto
): Boolean {
    // Eksisterende oppgave er på vent, ventebehov med frist i dag er sist endret av Kelvin, og behandlingen er ikke lenger på vent
    return eksisterendeOppgave.påVentTil != null && oppgaveOppdatering.tattAvVentAutomatisk && oppgaveOppdatering.venteInformasjon?.frist == null
}

private fun AvklaringsbehovHendelse.sistEndret() = sisteEndring().tidsstempel