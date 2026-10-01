package workshop.saga.account.domain

import workshop.saga.contracts.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewParticipantTest {

    @Test
    fun `cadastro valido vira conta com o saldo inicial`() {
        val account = NewParticipant(" Maria Souza ", "maria", Money(100_000)).toAccount()

        assertEquals(Account("maria", "Maria Souza", Money(100_000)), account)
    }

    @Test
    fun `recusa chave reservada, chave invalida, nome vazio e saldo negativo`() {
        assertFailsWith<IllegalArgumentException> { NewParticipant("X", "conta-encerrada", Money(0)) }
        assertFailsWith<IllegalArgumentException> { NewParticipant("X", "Maria Souza", Money(0)) }
        assertFailsWith<IllegalArgumentException> { NewParticipant(" ", "maria", Money(0)) }
        assertFailsWith<IllegalArgumentException> { NewParticipant("X", "maria", Money(-1)) }
    }
}
