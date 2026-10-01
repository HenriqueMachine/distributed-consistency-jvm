package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.during
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.awaitState
import workshop.saga.e2e.WorkshopClient.createTransfer
import workshop.saga.e2e.WorkshopClient.createTransferExpectingFailure
import workshop.saga.e2e.WorkshopClient.newSender
import workshop.saga.e2e.WorkshopClient.participant
import workshop.saga.e2e.WorkshopClient.pixCredits
import workshop.saga.e2e.WorkshopClient.statement
import workshop.saga.e2e.WorkshopClient.transfer
import java.time.Duration

/** Cada falha que a apresentação sabe provocar, com o comportamento esperado neste passo. */
class FailureScenariosTest {

    @Test
    fun `CRASH_AFTER_SEND - transferencia e mensagem caem juntas, ninguem e debitado`() {
        val transferId = createTransferExpectingFailure(from = newSender(), simulate = "CRASH_AFTER_SEND")

        assertThat(transfer(transferId)).isNull()
        await during Duration.ofSeconds(3) atMost Duration.ofSeconds(5) untilAsserted {
            assertThat(statement(transferId).debits).isEmpty()
        }
    }

    @Test
    fun `DUPLICATE - a mensagem repetida e ignorada, um debito so`() {
        val sender = newSender(balance = "1000.00")

        val transfer = createTransfer(from = sender, simulate = "DUPLICATE")

        awaitState(transfer.id, "COMPLETED")
        await during Duration.ofSeconds(2) atMost Duration.ofSeconds(5) untilAsserted {
            assertThat(statement(transfer.id).debits).hasSize(1)
            assertThat(pixCredits(transfer.id)).hasSize(1)
            assertThat(participant(sender)?.balance).isEqualByComparingTo("850.00")
        }
    }

    @Test
    fun `DEBIT_SLOW - timeout vira UNKNOWN, o reenvio recebe o resultado anterior e a transferencia conclui`() {
        val sender = newSender(balance = "1000.00")

        val transfer = createTransfer(from = sender, simulate = "DEBIT_SLOW")

        awaitState(transfer.id, "COMPLETED", timeout = Duration.ofSeconds(20))
        assertThat(statement(transfer.id).debits).hasSize(1)
        assertThat(statement(transfer.id).refunds).isEmpty()
        assertThat(participant(sender)?.balance).isEqualByComparingTo("850.00")
    }

    @Test
    fun `quebra passo-5 - PIX_CRASH some com a mensagem e a transferencia fica parada`() {
        val transfer = createTransfer(from = newSender(), simulate = "PIX_CRASH")

        awaitState(transfer.id, "PIX_PENDING")
        await during Duration.ofSeconds(10) atMost Duration.ofSeconds(12) untilAsserted {
            assertThat(transfer(transfer.id)?.state).isEqualTo("PIX_PENDING")
        }
    }
}
