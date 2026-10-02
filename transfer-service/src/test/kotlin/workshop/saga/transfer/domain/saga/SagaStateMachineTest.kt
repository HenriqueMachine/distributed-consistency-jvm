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
    fun `created transfer requests the debit and starts the deadline`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(CREATED), SagaEvent.TransferPlaced))

        assertEquals(DEBIT_PENDING, decision.saga.state)
        assertEquals(listOf(DebitAccount(1042, "ana", 15_000)), decision.commands)
        assertEquals(now.plusSeconds(8), decision.saga.deadlineAt)
        assertEquals(1, decision.saga.attempts)
    }

    @Test
    fun `approved debit requests the Pix`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING), SagaEvent.AccountDebited("d-1")))

        assertEquals(PIX_PENDING, decision.saga.state)
        assertEquals(listOf(SendPix(1042, "henrique", 15_000)), decision.commands)
        assertEquals(now.plusSeconds(12), decision.saga.deadlineAt)
    }

    @Test
    fun `settled Pix completes the transfer`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING), SagaEvent.PixSettled("E1042")))

        assertEquals(COMPLETED, decision.saga.state)
        assertEquals(emptyList(), decision.commands)
    }

    @Test
    fun `rejected Pix compensates with a refund and then cancels`() {
        val refunding = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING), SagaEvent.PixRejected("conta encerrada")))
        assertEquals(REFUNDING, refunding.saga.state)
        assertEquals(listOf(RefundDebit(1042)), refunding.commands)

        val cancelled = assertIs<Decision.Transition>(decide(refunding.saga, SagaEvent.DebitRefunded("d-1")))
        assertEquals(CANCELLED, cancelled.saga.state)
    }

    @Test
    fun `declined debit cancels without compensating anything`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING), SagaEvent.DebitDeclined("saldo insuficiente")))

        assertEquals(CANCELLED, decision.saga.state)
        assertEquals(emptyList(), decision.commands)
    }

    @Test
    fun `timeout before the deadline is ignored`() {
        assertIs<Decision.Ignore>(decide(sagaIn(DEBIT_PENDING, deadlineAt = now.plusSeconds(1)), SagaEvent.TimedOut))
    }

    @Test
    fun `debit timeout leads to UNKNOWN and asks again with the same key, without compensating`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_PENDING, deadlineAt = now, attempts = 1), SagaEvent.TimedOut))

        assertEquals(DEBIT_UNKNOWN, decision.saga.state)
        assertEquals(listOf(DebitAccount(1042, "ana", 15_000)), decision.commands)
        assertEquals(2, decision.saga.attempts)
        assertEquals(now.plusSeconds(8), decision.saga.deadlineAt)
    }

    @Test
    fun `reply to the resend takes the saga out of UNKNOWN and moves on`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_UNKNOWN), SagaEvent.AccountDebited("d-1")))

        assertEquals(PIX_PENDING, decision.saga.state)
    }

    @Test
    fun `an explicit no in UNKNOWN cancels`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_UNKNOWN), SagaEvent.DebitDeclined("saldo insuficiente")))

        assertEquals(CANCELLED, decision.saga.state)
    }

    @Test
    fun `exhausted attempts lead to NEEDS_ATTENTION, without compensating`() {
        val decision = assertIs<Decision.Transition>(decide(sagaIn(DEBIT_UNKNOWN, deadlineAt = now, attempts = 3), SagaEvent.TimedOut))

        assertEquals(NEEDS_ATTENTION, decision.saga.state)
        assertEquals(emptyList(), decision.commands)
    }

    @Test
    fun `Pix timeout resends with the same key and, when exhausted, stops at NEEDS_ATTENTION`() {
        val retry = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING, deadlineAt = now, attempts = 1), SagaEvent.TimedOut))
        assertEquals(PIX_PENDING, retry.saga.state)
        assertEquals(listOf(SendPix(1042, "henrique", 15_000)), retry.commands)
        assertEquals(2, retry.saga.attempts)

        val giveUp = assertIs<Decision.Transition>(decide(sagaIn(PIX_PENDING, deadlineAt = now, attempts = 3), SagaEvent.TimedOut))
        assertEquals(NEEDS_ATTENTION, giveUp.saga.state)
        assertEquals(emptyList(), giveUp.commands)
    }

    @Test
    fun `compensation has a deadline too - resends the refund and, when exhausted, NEEDS_ATTENTION`() {
        val retry = assertIs<Decision.Transition>(decide(sagaIn(REFUNDING, deadlineAt = now, attempts = 1), SagaEvent.TimedOut))
        assertEquals(listOf(RefundDebit(1042)), retry.commands)

        val giveUp = assertIs<Decision.Transition>(decide(sagaIn(REFUNDING, deadlineAt = now, attempts = 3), SagaEvent.TimedOut))
        assertEquals(NEEDS_ATTENTION, giveUp.saga.state)
    }

    @Test
    fun `original reply arriving late after the resend is ignored`() {
        assertIs<Decision.Ignore>(decide(sagaIn(PIX_PENDING), SagaEvent.AccountDebited("d-1")))
    }

    @Test
    fun `reply arriving after the saga ended is ignored`() {
        assertIs<Decision.Ignore>(decide(sagaIn(CANCELLED), SagaEvent.AccountDebited("d-1")))
        assertIs<Decision.Ignore>(decide(sagaIn(COMPLETED), SagaEvent.PixSettled("E1042")))
        assertIs<Decision.Ignore>(decide(sagaIn(NEEDS_ATTENTION), SagaEvent.AccountDebited("d-1")))
        assertIs<Decision.Ignore>(decide(sagaIn(NEEDS_ATTENTION), SagaEvent.TimedOut))
    }

    @Test
    fun `Mortician rescue - success arriving in NEEDS_ATTENTION completes the saga`() {
        val settled = assertIs<Decision.Transition>(decide(sagaIn(NEEDS_ATTENTION), SagaEvent.PixSettled("E1042")))
        assertEquals(COMPLETED, settled.saga.state)

        val refunded = assertIs<Decision.Transition>(decide(sagaIn(NEEDS_ATTENTION), SagaEvent.DebitRefunded("d-1")))
        assertEquals(CANCELLED, refunded.saga.state)
    }
}
