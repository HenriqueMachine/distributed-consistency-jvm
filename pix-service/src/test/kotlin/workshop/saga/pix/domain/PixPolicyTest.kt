package workshop.saga.pix.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PixPolicyTest {

    @Test
    fun `any key receives`() {
        assertEquals(PixDecision.Send, PixPolicy.evaluate("henrique"))
        assertEquals(PixDecision.Send, PixPolicy.evaluate("joao"))
    }

    @Test
    fun `closed account is rejected`() {
        assertIs<PixDecision.Reject>(PixPolicy.evaluate("conta-encerrada"))
    }
}
