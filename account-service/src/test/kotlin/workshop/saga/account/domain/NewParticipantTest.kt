package workshop.saga.account.domain

import workshop.saga.contracts.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewParticipantTest {

    @Test
    fun `valid registration becomes an account with the initial balance`() {
        val account = NewParticipant(" Maria Souza ", "maria", Money(100_000)).toAccount()

        assertEquals(Account("maria", "Maria Souza", Money(100_000)), account)
    }

    @Test
    fun `rejects reserved key, invalid key, blank name and negative balance`() {
        assertFailsWith<IllegalArgumentException> { NewParticipant("X", "conta-encerrada", Money(0)) }
        assertFailsWith<IllegalArgumentException> { NewParticipant("X", "Maria Souza", Money(0)) }
        assertFailsWith<IllegalArgumentException> { NewParticipant(" ", "maria", Money(0)) }
        assertFailsWith<IllegalArgumentException> { NewParticipant("X", "maria", Money(-1)) }
    }
}
