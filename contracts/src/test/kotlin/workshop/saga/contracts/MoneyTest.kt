package workshop.saga.contracts

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `converts reais to cents and formats like the statement`() {
        val money = Money.of(BigDecimal("150.00"))

        assertEquals(15_000, money.cents)
        assertEquals("R$ 150,00", money.toString())
    }

    @Test
    fun `adds, subtracts and compares without floating point`() {
        assertEquals(Money(850), Money(1_000) - Money(150))
        assertEquals(Money(1_150), Money(1_000) + Money(150))
        assertTrue(Money(100) < Money(150))
    }

    @Test
    fun `rejects fractions of a cent and amounts too large`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { Money.of(BigDecimal("1.005")) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { Money.of(BigDecimal("1e30")) }
        assertEquals(Money(150), Money.of(BigDecimal("1.50000")))
    }
}
