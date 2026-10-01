package workshop.saga.transfer.domain.saga

import workshop.saga.contracts.DebitAccount
import workshop.saga.contracts.Money
import workshop.saga.contracts.RefundDebit
import workshop.saga.contracts.SendPix
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.SagaState.CANCELLED
import workshop.saga.transfer.domain.saga.SagaState.COMPLETED
import workshop.saga.transfer.domain.saga.SagaState.CREATED
import workshop.saga.transfer.domain.saga.SagaState.DEBIT_PENDING
import workshop.saga.transfer.domain.saga.SagaState.PIX_PENDING
import workshop.saga.transfer.domain.saga.SagaState.REFUNDING
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SagaStateMachineTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val machine = SagaStateMachine(SagaTimeouts(debit = Duration.ofSeconds(8)))
    private val transfer = Transfer(TransferId(1042), "ana", "henrique", Money(15_000))

    private fun sagaIn(state: SagaState, deadlineAt: Instant? = null) = Saga(transfer.id, state, deadlineAt)

    private fun decide(saga: Saga, event: SagaEvent, at: Instant = now) = machine.decide(transfer, saga, event, at)

    @Test
    fun `transferencia criada pede o debito e comeca a contar o prazo`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(CREATED), SagaEvent.TransferPlaced))

        assertEquals(DEBIT_PENDING, decision.saga.state)
        assertEquals(listOf(DebitAccount(1042, "ana", 15_000)), decision.commands)
        assertEquals(now.plusSeconds(8), decision.saga.deadlineAt)
    }

    @Test
    fun `debito aprovado pede o Pix`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING), SagaEvent.AccountDebited("d-1")))

        assertEquals(PIX_PENDING, decision.saga.state)
        assertEquals(listOf(SendPix(1042, "henrique", 15_000)), decision.commands)
    }

    @Test
    fun `Pix liquidado conclui a transferencia`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING), SagaEvent.PixSettled("E1042")))

        assertEquals(COMPLETED, decision.saga.state)
        assertEquals(emptyList(), decision.commands)
    }

    @Test
    fun `Pix recusado compensa com estorno e depois cancela`() {
        val refunding = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING), SagaEvent.PixRejected("conta encerrada")))
        assertEquals(REFUNDING, refunding.saga.state)
        assertEquals(listOf(RefundDebit(1042)), refunding.commands)

        val cancelled = assertIs<Decision.Transition>(decide(refunding.saga, SagaEvent.DebitRefunded("d-1")))
        assertEquals(CANCELLED, cancelled.saga.state)
    }

    @Test
    fun `debito recusado cancela sem compensar nada`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING), SagaEvent.DebitDeclined("saldo insuficiente")))

        assertEquals(CANCELLED, decision.saga.state)
        assertEquals(emptyList(), decision.commands)
    }

    @Test
    fun `timeout antes do prazo e ignorado`() {
        assertIs<Decision.Ignore>(decide(sagaIn(DEBIT_PENDING, deadlineAt = now.plusSeconds(1)), SagaEvent.TimedOut))
    }

    @Test
    fun `quebra passo-4 - timeout do debito e tratado como falha e dispara estorno`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING, deadlineAt = now), SagaEvent.TimedOut))

        assertEquals(REFUNDING, decision.saga.state)
        assertEquals(listOf(RefundDebit(1042)), decision.commands)
    }

    @Test
    fun `resposta que chega depois do fim da saga e ignorada`() {
        assertIs<Decision.Ignore>(decide(sagaIn(CANCELLED), SagaEvent.AccountDebited("d-1")))
        assertIs<Decision.Ignore>(decide(sagaIn(COMPLETED), SagaEvent.PixSettled("E1042")))
    }
}
