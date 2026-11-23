package no.nav.aap.oppgave

import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomBatchData
import no.nav.aap.oppgave.klienter.nom.ansattinfo.NomResultCode
import java.time.LocalDate


fun harSluttetEllerFinnesIkke(resultat: NomBatchData): Boolean {
    return when (resultat.code) {
        NomResultCode.NOT_FOUND -> true
        NomResultCode.OK -> {
            val ressurs = checkNotNull(resultat.ressurs) {
                "NOM returnerte OK uten ressurs"
            }
            val sluttdato = ressurs.sluttdato
            if (sluttdato == null) {
                false
            } else {
                LocalDate.parse(sluttdato).isBefore(LocalDate.now())
            }
        }
        NomResultCode.ERROR,
        NomResultCode.NOT_IN_FILTER -> error(
            "Kan ikke avgjøre om ansatt har sluttet: ${resultat.code}"
        )
    }
}
