package workshop.saga.account.domain

import workshop.saga.contracts.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DebitPolicyTest {

    private val ana = Account("ana", "Ana", Money(100_000))

    @Test
    fun `approves and subtracts from the balance`() {
        val decision = assertIs<DebitDecision.Approve>(DebitPolicy.evaluate(ana, "ana", Money(15_000)))

        assertEquals(Money(85_000), decision.remaining.balance)
    }

    @Test
    fun `approves exactly the whole balance`() {
        assertIs<DebitDecision.Approve>(DebitPolicy.evaluate(ana, "ana", Money(100_000)))
    }

    @Test
    fun `declines insufficient balance and unknown account`() {
        assertIs<DebitDecision.Decline>(DebitPolicy.evaluate(ana, "ana", Money(100_001)))
        assertIs<DebitDecision.Decline>(DebitPolicy.evaluate(null, "fantasma", Money(1)))
    }
}
