package no.nav.aap.oppgave.filter

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class FilterRepositoryTest {

    private val dataSource = InitTestDatabase.freshDatabase()

    @BeforeEach
    fun setup() {
        @Suppress("SqlWithoutWhere")
        dataSource.transaction {
            it.execute("DELETE FROM FILTER_AVKLARINGSBEHOVTYPE")
            it.execute("DELETE FROM FILTER_BEHANDLINGSTYPE")
            it.execute("DELETE FROM FILTER_ENHET")
            it.execute("DELETE FROM FILTER")
        }
    }

    @Test
    fun `Opprett enkelt filter og hent det ut igjen`() {
        dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Basic filter",
                beskrivelse = "Basic filter",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now()
            )
            val antallFilterFørTest = filterRepo.hentAlle().size
            val filterId = filterRepo.opprett(nyttFilter)

            val filter = filterRepo.hent(filterId)
            assertThat(filter).isNotNull()
            assertThat(filter!!.navn).isEqualTo("Basic filter")

            val alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(antallFilterFørTest + 1)
        }
    }

    @Test
    fun `Kan opprette enhetsspesifikt filter`() {
        dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Filter for enhet",
                beskrivelse = "Filter for enhet",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
                enhetFilter = listOf(EnhetFilter("1234", Filtermodus.INKLUDER))
            )
            val antallFilterFørTest = filterRepo.hentAlle().size
            val filterId = filterRepo.opprett(nyttFilter)

            val ekskludertFilter = OpprettFilter(
                navn = "Ikke 1234",
                beskrivelse = "Filter som ikke 1234 har tilgang til",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
                enhetFilter = listOf(
                    EnhetFilter("ALLE", Filtermodus.INKLUDER),
                    EnhetFilter("1234", Filtermodus.EKSKLUDER)
                )
            )
            filterRepo.opprett(ekskludertFilter)

            val filtre = filterRepo.hentForEnheter(listOf("1234"))
            assertThat(filtre).hasSize(antallFilterFørTest + 1)
            assertThat(filtre.first().navn).isEqualTo("Filter for enhet")
            assertThat(filtre.first().id).isEqualTo(filterId)
        }
    }

    @Test
    fun `Opprett filter med avklaringsbehov`() {
        dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Filter for avklar sykdom oppgave",
                beskrivelse = "Filter for avklar sykdom oppgave",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
                avklaringsbehovtyper = setOf(Definisjon.AVKLAR_SYKDOM.kode.name)
            )
            val antallFilterFørTest = filterRepo.hentAlle().size
            val opprettetFilterId = filterRepo.opprett(nyttFilter)

            val opprettetFilter = filterRepo.hent(opprettetFilterId)!!
            assertThat(opprettetFilter.navn).isEqualTo("Filter for avklar sykdom oppgave")
            assertThat(opprettetFilter.behandlingstyper).hasSize(0)
            assertThat(opprettetFilter.avklaringsbehovKoder).hasSize(1)
            assertThat(opprettetFilter.avklaringsbehovKoder.contains(Definisjon.AVKLAR_SYKDOM.kode.name)).isTrue()
        }
    }

    @Test
    fun `Opprett filter med behandlingstype'`() {
        dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Filter for førstegangsbehandling",
                beskrivelse = "Filter for førstegangsbehandling",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
                behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING)
            )
            val antallFilterFørTest = filterRepo.hentAlle().size
            filterRepo.opprett(nyttFilter)

            val alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(antallFilterFørTest + 1)
            assertThat(alleFilter.first().navn).isEqualTo("Filter for førstegangsbehandling")
            assertThat(alleFilter.first().avklaringsbehovKoder).hasSize(0)
            assertThat(alleFilter.first().behandlingstyper).hasSize(1)
            assertThat(alleFilter.first().behandlingstyper.contains(Behandlingstype.FØRSTEGANGSBEHANDLING)).isTrue()
        }
    }

    @Test
    fun `Oppdater filter med både behandlingstype og avklaringsbehov`() {
        dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Filter for avklar sykdom oppgave og førstegangsbehandling",
                beskrivelse = "Filter for avklar sykdom oppgave og førstegangsbehandling",
                opprettetAv = "test1",
                opprettetTidspunkt = LocalDateTime.now(),
                behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING),
                avklaringsbehovtyper = setOf(Definisjon.AVKLAR_SYKDOM.kode.name)
            )

            val antallFilterFørTest = filterRepo.hentAlle().size
            val filterId = filterRepo.opprett(nyttFilter)

            var alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(antallFilterFørTest + 1)
            var hentetFilter = alleFilter.first()
            assertThat(hentetFilter.navn).isEqualTo("Filter for avklar sykdom oppgave og førstegangsbehandling")
            assertThat(hentetFilter.behandlingstyper).hasSize(1)
            assertThat(hentetFilter.behandlingstyper.contains(Behandlingstype.FØRSTEGANGSBEHANDLING)).isTrue()
            assertThat(hentetFilter.avklaringsbehovKoder).hasSize(1)
            assertThat(hentetFilter.avklaringsbehovKoder.contains(Definisjon.AVKLAR_SYKDOM.kode.name)).isTrue()

            val oppdaterFilter = OppdaterFilter(
                id = filterId,
                navn = "Filter for avklar barnetillegg og revurdering",
                beskrivelse = "Filter for avklar barnetillegg og revurdering",
                behandlingstyper = setOf(Behandlingstype.REVURDERING),
                avklaringsbehovtyper = setOf(Definisjon.AVKLAR_BARNETILLEGG.kode.name),
                endretAv = "test2",
                endretTidspunkt = LocalDateTime.now()
            )
            filterRepo.oppdater(oppdaterFilter)

            alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(1)
            hentetFilter = alleFilter.first()
            assertThat(hentetFilter.navn).isEqualTo("Filter for avklar barnetillegg og revurdering")
            assertThat(hentetFilter.behandlingstyper).hasSize(1)
            assertThat(hentetFilter.behandlingstyper.contains(Behandlingstype.REVURDERING)).isTrue()
            assertThat(hentetFilter.avklaringsbehovKoder).hasSize(1)
            assertThat(hentetFilter.avklaringsbehovKoder.contains(Definisjon.AVKLAR_BARNETILLEGG.kode.name)).isTrue()
        }
    }

    @Test
    fun `Logisk sletting av filter`() {
        dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Test filter",
                beskrivelse = "Test filter",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now()
            )
            val filterId = filterRepo.opprett(nyttFilter)

            var alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(1)

            filterRepo.slettFilter(filterId)

            alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(0)
        }
    }

}