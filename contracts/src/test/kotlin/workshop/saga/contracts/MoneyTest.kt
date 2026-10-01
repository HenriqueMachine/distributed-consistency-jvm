package workshop.saga.contracts

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun `converte reais em centavos e formata como no extrato`() {
        val money = Money.of(BigDecimal("150.00"))

        assertEquals(15_000, money.cents)
        assertEquals("R$ 150,00", money.toString())
    }

    @Test
    fun `soma, subtrai e compara sem ponto flutuante`() {
        assertEquals(Money(850), Money(1_000) - Money(150))
        assertEquals(Money(1_150), Money(1_000) + Money(150))
        assertTrue(Money(100) < Money(150))
    }

    @Test
    fun `recusa fracao de centavo e valor grande demais`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { Money.of(BigDecimal("1.005")) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { Money.of(BigDecimal("1e30")) }
        assertEquals(Money(150), Money.of(BigDecimal("1.50000")))
    }
}
