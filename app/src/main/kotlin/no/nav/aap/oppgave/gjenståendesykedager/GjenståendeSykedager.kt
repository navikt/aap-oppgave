package no.nav.aap.oppgave.gjenståendesykedager

import no.nav.aap.oppgave.GjenståendeSykedagerDto
import no.nav.aap.oppgave.GjenståendeSykedagerGrunnlag
import java.time.LocalDate

data class GjenståendeSykedager(
    val grunnlag: GjenståendeSykedagerGrunnlag,
    val gjenståendeDager: Int?,
    val vurdertTilOgMed: LocalDate?,
    val maksdato: LocalDate?,
) {
    fun tilDto(): GjenståendeSykedagerDto = GjenståendeSykedagerDto(
        grunnlag = grunnlag,
        gjenståendeDager = gjenståendeDager,
        vurdertTilOgMed = vurdertTilOgMed,
        maksdato = maksdato,
    )
}
