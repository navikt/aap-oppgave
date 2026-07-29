package no.nav.aap.oppgave.enhet

class EnhetInfo(
    val enhetNr: String, val navn: String
) {
    fun tilDto(): EnhetDto {
        return EnhetDto(enhetNr, navn)
    }
}