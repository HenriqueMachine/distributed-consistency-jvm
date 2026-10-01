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
    fun `quebra passo-4 - DEBIT_SLOW - o timeout estorna um debito que deu certo e cancela`() {
        val sender = newSender(balance = "1000.00")

        val transfer = createTransfer(from = sender, simulate = "DEBIT_SLOW")

        awaitState(transfer.id, "CANCELLED", timeout = Duration.ofSeconds(20))
        // A aprovação chega aos 15 s e é ignorada: a transferência ia passar, mas foi estornada.
        await during Duration.ofSeconds(8) atMost Duration.ofSeconds(10) untilAsserted {
            assertThat(transfer(transfer.id)?.state).isEqualTo("CANCELLED")
        }
        assertThat(statement(transfer.id).debits).hasSize(1)
        assertThat(statement(transfer.id).refunds).hasSize(1)
        assertThat(pixCredits(transfer.id)).isEmpty()
    }
}
