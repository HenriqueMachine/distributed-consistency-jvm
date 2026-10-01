package workshop.saga.pix.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PixPolicyTest {

    @Test
    fun `qualquer chave recebe`() {
        assertEquals(PixDecision.Send, PixPolicy.evaluate("henrique"))
        assertEquals(PixDecision.Send, PixPolicy.evaluate("joao"))
    }

    @Test
    fun `conta encerrada e recusada`() {
        assertIs<PixDecision.Reject>(PixPolicy.evaluate("conta-encerrada"))
    }
}
