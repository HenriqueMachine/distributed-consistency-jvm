package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.awaitState
import workshop.saga.e2e.WorkshopClient.createTransfer
import workshop.saga.e2e.WorkshopClient.newSender
import workshop.saga.e2e.WorkshopClient.participant
import workshop.saga.e2e.WorkshopClient.pixCredits
import workshop.saga.e2e.WorkshopClient.statement

/** Os caminhos normais da saga: sem nenhuma falha simulada. */
class SagaFlowTest {

    @Test
    fun `happy path - debits, settles the Pix and completes`() {
        val sender = newSender(balance = "1000.00")

        val transfer = createTransfer(from = sender, to = "henrique", amount = "150.00")

        assertThat(transfer.id).isGreaterThanOrEqualTo(1042)
        awaitState(transfer.id, "COMPLETED")
        assertThat(statement(transfer.id).debits).hasSize(1)
        assertThat(pixCredits(transfer.id)).hasSize(1)
        assertThat(participant(sender)?.balance).isEqualByComparingTo("850.00")
    }

    @Test
    fun `closed destination account - refunds and cancels`() {
        val sender = newSender(balance = "1000.00")

        val transfer = createTransfer(from = sender, to = "conta-encerrada")

        awaitState(transfer.id, "CANCELLED")
        assertThat(statement(transfer.id).debits).hasSize(1)
        assertThat(statement(transfer.id).refunds).hasSize(1)
        assertThat(pixCredits(transfer.id)).isEmpty()
        assertThat(participant(sender)?.balance).isEqualByComparingTo("1000.00")
    }

    @Test
    fun `insufficient balance - cancels without debiting`() {
        val sender = newSender(balance = "100.00")

        val transfer = createTransfer(from = sender, amount = "150.00")

        awaitState(transfer.id, "CANCELLED")
        assertThat(statement(transfer.id).debits).isEmpty()
        assertThat(participant(sender)?.balance).isEqualByComparingTo("100.00")
    }
}
