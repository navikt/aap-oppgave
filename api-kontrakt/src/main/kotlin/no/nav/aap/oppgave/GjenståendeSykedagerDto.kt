package no.nav.aap.oppgave

import java.time.LocalDate

data class GjenståendeSykedagerDto(
    val grunnlag: GjenståendeSykedagerGrunnlag,
    val gjenståendeDager: Int?,
    val vurdertTilOgMed: LocalDate?,
    val maksdato: LocalDate?,
)

enum class GjenståendeSykedagerGrunnlag {
    VEDTAK_I_NY_LØSNING,
    SISTE_UTBETALING_I_INFOTRYGD,
    MAKSDATO_I_INFOTRYGD,
    MANGLER_MAKSDATO_I_INFOTRYGD,
    INGEN_SYKEPENGER_SISTE_26_UKER,
}
