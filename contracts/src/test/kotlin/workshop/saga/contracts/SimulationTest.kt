package workshop.saga.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SimulationTest {

    @Test
    fun `missing or blank header means no simulation`() {
        assertNull(Simulation.parse(null))
        assertNull(Simulation.parse("  "))
    }

    @Test
    fun `accepts the name case-insensitively`() {
        assertEquals(Simulation.CRASH_AFTER_SEND, Simulation.parse("crash_after_send"))
    }

    @Test
    fun `unknown value is rejected`() {
        assertFailsWith<IllegalArgumentException> { Simulation.parse("EXPLODIR") }
    }
}
