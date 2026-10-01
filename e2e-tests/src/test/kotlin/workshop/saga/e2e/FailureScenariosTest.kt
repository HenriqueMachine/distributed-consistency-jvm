package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.during
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
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
import workshop.saga.e2e.WorkshopKafka.header
import java.time.Duration
import java.util.UUID

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
    fun `PIX_CRASH - retry com backoff, DLT a cada tentativa da saga e NEEDS_ATTENTION`() {
        val transfer = createTransfer(from = newSender(), simulate = "PIX_CRASH")

        // 3 tentativas da saga × (1 s + 2 s + 4 s de retry) com prazo de 12 s cada.
        awaitState(transfer.id, "NEEDS_ATTENTION", timeout = Duration.ofSeconds(60))
        val deadLetters = WorkshopKafka.recordsWithKey("pix.commands.DLT", transfer.id.toString())
        assertThat(deadLetters).hasSize(3)
        assertThat(deadLetters.first().header("kafka_dlt-exception-message")).contains("falha ao processar o Pix")
        assertThat(LogFiles.of("pix-service")).anyMatch { it.contains("${transfer.id} falha tentativa=3 → pix.commands.DLT") }
    }

    @Test
    fun `payload invalido vai direto para a DLT, sem retry`() {
        val key = "invalid-${UUID.randomUUID()}"
        val sentAt = System.currentTimeMillis()
        WorkshopKafka.send(
            topic = "pix.commands",
            key = key,
            value = "{isto não é json",
            headers = mapOf("messageId" to UUID.randomUUID().toString(), "messageType" to "SendPix"),
        )

        val deadLetter = await atMost Duration.ofSeconds(15) untilCallTo {
            WorkshopKafka.recordsWithKey("pix.commands.DLT", key).singleOrNull()
        } matches { it != null }

        // Sem retry: chega à DLT bem antes de 1 s + 2 s + 4 s.
        assertThat(Duration.ofMillis(checkNotNull(deadLetter).timestamp() - sentAt)).isLessThan(Duration.ofSeconds(3))
        assertThat(deadLetter.header("kafka_dlt-exception-cause-fqcn")).endsWith("InvalidPayloadException")
    }
}
