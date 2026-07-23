package no.nav.aap.oppgave.oppgaveliste

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import no.nav.aap.oppgave.BehandlingskontekstResponse
import no.nav.aap.oppgave.ForrigeKvalitetssikrerInfo
import no.nav.aap.oppgave.Oppgave
import no.nav.aap.oppgave.ReturInformasjonDto
import no.nav.aap.oppgave.hent.SkjermingInfoResponse
import no.nav.aap.oppgave.hent.VenteInformasjonResponse
import no.nav.aap.oppgave.klienter.pdl.PdlGraphqlGateway
import no.nav.aap.oppgave.liste.ListeOppgaveResponse
import no.nav.aap.oppgave.liste.OppgaveMetadataResponse
import no.nav.aap.oppgave.liste.OppgavelisteTagsResponse
import no.nav.aap.oppgave.liste.PersonOgEnhetResponse
import no.nav.aap.oppgave.metrikker.prometheus
import org.flywaydb.core.api.logging.LogFactory
import java.time.Duration
import kotlin.time.measureTimedValue

object OppgavelisteUtils {
    private val logger = LogFactory.getLog(OppgavelisteUtils::class.java)

    private val pdlGraphqlGateway = PdlGraphqlGateway.withClientCredentialsRestClient()

    private val personinfoCache = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(Duration.ofHours(12))
        .recordStats()
        .build<String, String>() // <Ident, Navn>

    init {
        CaffeineCacheMetrics.monitor(prometheus, personinfoCache, "oppgave_navn")
    }

    fun List<Oppgave>.hentPersonNavn(): List<Oppgave> {
        val identer = mapNotNull { it.personIdent }.distinct()
        if (identer.isEmpty()) {
            return this
        }

        val (identerMangler, identerICache) = identer.partition { personinfoCache.getIfPresent(it) == null }

        val navnMapFraCache = identerICache
            .associateWith { personinfoCache.getIfPresent(it) }
            .filterNot { it.value.isNullOrBlank() }

        if (identerMangler.isEmpty()) {
            return this.map { it.medNavn(it.personIdent, navnMapFraCache) }
        }

        val navnMap = hentNavnFraPDL(identerMangler) + navnMapFraCache

        return this.map { it.medNavn(it.personIdent, navnMap) }
    }

    private fun hentNavnFraPDL(identer: List<String>): Map<String, String> = measureTimedValue {
        runCatching {
            identer.chunked(1000)
                .flatMap { bolk ->
                    hentNavnForBolk(bolk)
                }.toMap()
        }
    }.let { (result, duration) ->
        if (result.isSuccess) logger.info("Hentet navn for ${identer.size} identer fra PDL (tid: $duration)")
        else logger.error("Feil ved henting av ${identer.size} identer fra PDL (tid: $duration)")

        result.getOrThrow()
    }

    private fun hentNavnForBolk(identer: List<String>): List<Pair<String, String>> {
        return pdlGraphqlGateway
            .hentPersoninfoForIdenter(identer)
            .hentPersonBolk
            ?.map {
                val navn = it.person?.navn?.firstOrNull()?.fulltNavn() ?: ""
                personinfoCache.put(it.ident, navn)
                it.ident to navn
            }
            ?: emptyList()
    }

    private fun Oppgave.medNavn(
        ident: String?,
        samletNavnMap: Map<String, String?>
    ): Oppgave {
        val personNavn = ident?.let { samletNavnMap[it] } ?: ""

        return this.copy(personNavn = personNavn)
    }

    fun Oppgave.tilListeOppgaveResponse(): ListeOppgaveResponse {
        return ListeOppgaveResponse(
            behandlingOpprettet = behandlingOpprettet,
            avklaringsbehovKode = avklaringsbehovKode,
            vurderingsbehov = vurderingsbehov,
            årsakTilOpprettelse = årsakTilOpprettelse,
            oppgaveMetadataResponse = OppgaveMetadataResponse(
                id = requireNotNull(id) { "Oppgave må ha ID" },
                versjon = versjon,
                status = status,
                opprettetTidspunkt = opprettetTidspunkt
            ),
            behandlingskontekstResponse = BehandlingskontekstResponse(
                behandlingsreferanse = behandlingRef,
                journalpostId = journalpostId,
                saksnummer = saksnummer,
                behandlingstype = behandlingstype,
                tilbakekrevingUrl = tilbakekrevingsVars?.tilbakekrevings_URL
            ),
            personOgEnhetResponse = PersonOgEnhetResponse(
                personIdent = requireNotNull(personIdent) { "Oppgave må ha personIdent" },
                personNavn = personNavn,
                enhet = enhet,
                oppfølgingsenhet = oppfølgingsenhet,
                enhetForrigeOppgave = enhetForrigeOppgave
            ),
            oppgavelisteTagsResponse = OppgavelisteTagsResponse(
                påVentInfo = påVentTil?.let {
                    VenteInformasjonResponse(
                        påVentTil = påVentTil,
                        påVentÅrsak = requireNotNull(påVentÅrsak) { "Oppgave har ventefrist men ikke årsak" },
                        venteBegrunnelse = venteBegrunnelse
                    )
                },
                forrigePåVentInfo = utløptVentefrist?.let {
                    VenteInformasjonResponse(
                        påVentTil = utløptVentefrist,
                        påVentÅrsak = forrigePåVentÅrsak,
                        venteBegrunnelse = forrigeVenteBegrunnelse
                    )
                },
                returInformasjon = returInformasjon?.let {
                    ReturInformasjonDto(
                        status = it.status,
                        årsaker = it.årsaker,
                        begrunnelse = it.begrunnelse,
                        endretAv = it.endretAv
                    )
                },
                skjermingInfoResponse = SkjermingInfoResponse(
                    harStrengtFortroligAdresse = harStrengtFortroligAdresse,
                    harFortroligAdresse = harFortroligAdresse == true,
                    erSkjermet = erSkjermet == true
                ),
                harUlesteDokumenter = harUlesteDokumenter == true,
                markeringer = markeringer,
                forrigeKvalitetssikrerInfo = forrigeKvalitetssikrerInfo?.let {
                    ForrigeKvalitetssikrerInfo(
                        forrigeKvalitetssikrerIdent = it.forrigeKvalitetssikrerIdent,
                        forrigeKvalitetssikrerNavn = it.forrigeKvalitetssikrerNavn
                    )
                }
            ),
            veilederArbeid = veilederArbeid,
            veilederSykdom = veilederSykdom,
            reservertAv = reservertAv,
            reservertAvNavn = reservertAvNavn,
            tilbakekrevingsVarsDto = tilbakekrevingsVars?.tilDto()
        )
    }
}

