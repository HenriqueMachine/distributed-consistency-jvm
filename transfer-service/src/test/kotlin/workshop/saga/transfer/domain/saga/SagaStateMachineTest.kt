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
import workshop.saga.transfer.domain.saga.SagaState.DEBIT_UNKNOWN
import workshop.saga.transfer.domain.saga.SagaState.NEEDS_ATTENTION
import workshop.saga.transfer.domain.saga.SagaState.PIX_PENDING
import workshop.saga.transfer.domain.saga.SagaState.REFUNDING
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SagaStateMachineTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val machine = SagaStateMachine(
        SagaTimeouts(
            debit = Duration.ofSeconds(8),
            pix = Duration.ofSeconds(12),
            refund = Duration.ofSeconds(8),
            maxAttempts = 3,
        ),
    )
    private val transfer = Transfer(TransferId(1042), "ana", "henrique", Money(15_000))

    private fun sagaIn(state: SagaState, deadlineAt: Instant? = null, attempts: Int = 1) =
        Saga(transfer.id, state, deadlineAt, attempts)

    private fun decide(saga: Saga, event: SagaEvent, at: Instant = now) = machine.decide(transfer, saga, event, at)

    @Test
    fun `transferencia criada pede o debito e comeca a contar o prazo`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(CREATED), SagaEvent.TransferPlaced))

        assertEquals(DEBIT_PENDING, decision.saga.state)
        assertEquals(listOf(DebitAccount(1042, "ana", 15_000)), decision.commands)
        assertEquals(now.plusSeconds(8), decision.saga.deadlineAt)
        assertEquals(1, decision.saga.attempts)
    }

    @Test
    fun `debito aprovado pede o Pix`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING), SagaEvent.AccountDebited("d-1")))

        assertEquals(PIX_PENDING, decision.saga.state)
        assertEquals(listOf(SendPix(1042, "henrique", 15_000)), decision.commands)
        assertEquals(now.plusSeconds(12), decision.saga.deadlineAt)
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
    fun `timeout do debito leva a UNKNOWN e pergunta de novo com a mesma chave, sem compensar`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING, deadlineAt = now, attempts = 1), SagaEvent.TimedOut))

        assertEquals(DEBIT_UNKNOWN, decision.saga.state)
        assertEquals(listOf(DebitAccount(1042, "ana", 15_000)), decision.commands)
        assertEquals(2, decision.saga.attempts)
        assertEquals(now.plusSeconds(8), decision.saga.deadlineAt)
    }

    @Test
    fun `resposta ao reenvio tira a saga de UNKNOWN e segue o fluxo`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_UNKNOWN), SagaEvent.AccountDebited("d-1")))

        assertEquals(PIX_PENDING, decision.saga.state)
    }

    @Test
    fun `um nao explicito em UNKNOWN cancela`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_UNKNOWN), SagaEvent.DebitDeclined("saldo insuficiente")))

        assertEquals(CANCELLED, decision.saga.state)
    }

    @Test
    fun `tentativas esgotadas levam a NEEDS_ATTENTION, sem compensar`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_UNKNOWN, deadlineAt = now, attempts = 3), SagaEvent.TimedOut))

        assertEquals(NEEDS_ATTENTION, decision.saga.state)
        assertEquals(emptyList(), decision.commands)
    }

    @Test
    fun `timeout do Pix reenvia com a mesma chave e, esgotado, para em NEEDS_ATTENTION`() {
        val retry = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING, deadlineAt = now, attempts = 1), SagaEvent.TimedOut))
        assertEquals(PIX_PENDING, retry.saga.state)
        assertEquals(listOf(SendPix(1042, "henrique", 15_000)), retry.commands)
        assertEquals(2, retry.saga.attempts)

        val giveUp = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING, deadlineAt = now, attempts = 3), SagaEvent.TimedOut))
        assertEquals(NEEDS_ATTENTION, giveUp.saga.state)
        assertEquals(emptyList(), giveUp.commands)
    }

    @Test
    fun `compensacao tambem tem prazo - reenvia o estorno e, esgotado, NEEDS_ATTENTION`() {
        val retry = assertIs<Decision.Transition>(decide(sagaIn(REFUNDING, deadlineAt = now, attempts = 1), SagaEvent.TimedOut))
        assertEquals(listOf(RefundDebit(1042)), retry.commands)

        val giveUp = assertIs<Decision.Transition>(decide(sagaIn(REFUNDING, deadlineAt = now, attempts = 3), SagaEvent.TimedOut))
        assertEquals(NEEDS_ATTENTION, giveUp.saga.state)
    }

    @Test
    fun `resposta original que chega atrasada depois do reenvio e ignorada`() {
        assertIs<Decision.Ignore>(decide(sagaIn(PIX_PENDING), SagaEvent.AccountDebited("d-1")))
    }

    @Test
    fun `resposta que chega depois do fim da saga e ignorada`() {
        assertIs<Decision.Ignore>(decide(sagaIn(CANCELLED), SagaEvent.AccountDebited("d-1")))
        assertIs<Decision.Ignore>(decide(sagaIn(COMPLETED), SagaEvent.PixSettled("E1042")))
        assertIs<Decision.Ignore>(decide(sagaIn(NEEDS_ATTENTION), SagaEvent.AccountDebited("d-1")))
    }
}
