package no.nav.aap.oppgave.filter

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.dbtest.InitTestDatabase
import no.nav.aap.oppgave.verdityper.Behandlingstype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.AfterTest

class FilterRepositoryTest {

    @AfterTest
    fun tearDown() {
        @Suppress("SqlWithoutWhere")
        InitTestDatabase.dataSource.transaction {
            it.execute("DELETE FROM FILTER_AVKLARINGSBEHOVTYPE")
            it.execute("DELETE FROM FILTER_BEHANDLINGSTYPE")
            it.execute("DELETE FROM FILTER")
        }
    }

    @Test
    fun `Opprett enkelt filter og hent det ut igjen`() {
        InitTestDatabase.dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Basic filter",
                beskrivelse = "Basic filter",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now()
            )
            val filterId = filterRepo.opprett(nyttFilter)

            val filter = filterRepo.hent(filterId)
            assertThat(filter).isNotNull()
            assertThat(filter!!.navn).isEqualTo("Basic filter")

            val alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(1)
            assertThat(alleFilter.first().navn).isEqualTo("Basic filter")
        }
    }

    @Test
    fun `Opprett filter med avklaringsbehov`() {
        InitTestDatabase.dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Filter for avklar sykdom oppgave",
                beskrivelse = "Filter for avklar sykdom oppgave",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
                avklaringsbehovtyper = setOf(Definisjon.AVKLAR_SYKDOM.kode.name)
            )
            filterRepo.opprett(nyttFilter)

            val alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(1)
            assertThat(alleFilter.first().navn).isEqualTo("Filter for avklar sykdom oppgave")
            assertThat(alleFilter.first().behandlingstyper).hasSize(0)
            assertThat(alleFilter.first().avklaringsbehovKoder).hasSize(1)
            assertThat(alleFilter.first().avklaringsbehovKoder.contains(Definisjon.AVKLAR_SYKDOM.kode.name)).isTrue()
        }
    }

    @Test
    fun `Opprett filter med behandlingstype'`() {
        InitTestDatabase.dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Filter for førstegangsbehandling",
                beskrivelse = "Filter for førstegangsbehandling",
                opprettetAv = "test",
                opprettetTidspunkt = LocalDateTime.now(),
                behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING)
            )
            filterRepo.opprett(nyttFilter)

            val alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(1)
            assertThat(alleFilter.first().navn).isEqualTo("Filter for førstegangsbehandling")
            assertThat(alleFilter.first().avklaringsbehovKoder).hasSize(0)
            assertThat(alleFilter.first().behandlingstyper).hasSize(1)
            assertThat(alleFilter.first().behandlingstyper.contains(Behandlingstype.FØRSTEGANGSBEHANDLING)).isTrue()
        }
    }

    @Test
    fun `Oppdater filter med både behandlingstype og avklaringsbehov`() {
        InitTestDatabase.dataSource.transaction { connection ->
            val filterRepo = FilterRepository(connection)
            val nyttFilter = OpprettFilter(
                navn = "Filter for avklar sykdom oppgave og førstegangsbehandling",
                beskrivelse = "Filter for avklar sykdom oppgave og førstegangsbehandling",
                opprettetAv = "test1",
                opprettetTidspunkt = LocalDateTime.now(),
                behandlingstyper = setOf(Behandlingstype.FØRSTEGANGSBEHANDLING),
                avklaringsbehovtyper = setOf(Definisjon.AVKLAR_SYKDOM.kode.name)
            )
            val filterId = filterRepo.opprett(nyttFilter)

            var alleFilter = filterRepo.hentAlle()
            assertThat(alleFilter).hasSize(1)
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
        InitTestDatabase.dataSource.transaction { connection ->
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