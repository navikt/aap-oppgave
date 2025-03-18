package no.nav.aap.oppgave.enhet

import no.nav.aap.oppgave.klienter.msgraph.Group
import no.nav.aap.oppgave.klienter.msgraph.IMsGraphClient
import no.nav.aap.oppgave.klienter.msgraph.MemberOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class EnhetServiceTest {
    @Test
    fun `lister kun opp enhets-roller`() {
        val graphClient = object : IMsGraphClient {
            override fun hentAdGrupper(currentToken: String, ident: String): MemberOf {
                return MemberOf(
                    groups = listOf(
                        Group(name = "0000-GA-ENHET_12345", id = UUID.randomUUID()),
                        Group(name = "0000-GA-GEO_12345", id = UUID.randomUUID())
                    )
                )
            }

        }
        val service = EnhetService(graphClient)

        val res = service.hentEnheter("xxx", "")
        assertThat(res).isNotEmpty()
        assertThat(res).hasSize(1)
        assertThat(res[0]).isEqualTo("12345")

    }
}