package workshop.saga.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SimulationTest {

    @Test
    fun `header ausente ou em branco significa nenhuma simulacao`() {
        assertNull(Simulation.parse(null))
        assertNull(Simulation.parse("  "))
    }

    @Test
    fun `aceita o nome sem diferenciar maiusculas`() {
        assertEquals(Simulation.CRASH_AFTER_SEND, Simulation.parse("crash_after_send"))
    }

    @Test
    fun `valor desconhecido e rejeitado`() {
        assertFailsWith<IllegalArgumentException> { Simulation.parse("EXPLODIR") }
    }
}
