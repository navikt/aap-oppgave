package no.nav.aap.oppgave.tilbakekreving

import no.nav.aap.oppgave.AvklaringsbehovKode

enum class TilbakeKrevingAvklaringsbehovKoder(val kode: String) {
    BESLUTTER_VEDTAK_TILBAKEKREVING("9083"),
    SAKSBEHANDLE_TILBAKEKREVING("9082");

    fun tilAvklaringsbehovKode(): AvklaringsbehovKode {
        return AvklaringsbehovKode(kode)
    }
    
    companion object {
        fun fraKode(kode: String): TilbakeKrevingAvklaringsbehovKoder {
            return entries.first { it.kode == kode }
        }
    }
}