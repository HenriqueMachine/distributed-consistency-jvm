package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.awaitState
import workshop.saga.e2e.WorkshopClient.createTransfer
import workshop.saga.e2e.WorkshopClient.metric
import workshop.saga.e2e.WorkshopClient.newSender
import workshop.saga.e2e.WorkshopKafka.header
import java.time.Duration

/** Passo 8: a saga tem que ser explicável por fora, sem abrir o banco (slides 46 e 47). */
class ObservabilityTest {

    @Test
    fun `DEBIT_SLOW - the cid tree tells debit attempt 1 apart from attempt 2`() {
        val transfer = createTransfer(from = newSender(), simulate = "DEBIT_SLOW")
        awaitState(transfer.id, "COMPLETED", timeout = Duration.ofSeconds(20))
        val root = "TRF-${transfer.id}"

        val debits = WorkshopKafka.recordsWithKey("account.commands", transfer.id.toString()).map { it.header("x-cid") }
        val pix = WorkshopKafka.recordsWithKey("pix.commands", transfer.id.toString()).map { it.header("x-cid") }
        val debitReplies = WorkshopKafka.recordsWithKey("account.replies", transfer.id.toString()).map { it.header("x-cid") }

        assertThat(debits).hasSize(2).allMatch { it!!.matches(Regex("""$root\.DEB-[0-9a-f]{2}""")) }
        assertThat(debits.distinct()).hasSize(2)
        assertThat(pix).singleElement().matches { it!!.startsWith("$root.PIX-") }
        // A resposta carrega o cid do comando que a originou.
        assertThat(debitReplies).allMatch { it in debits }
    }

    @Test
    fun `grep transferId and grep TRF tell the story, including the SPI call`() {
        val transfer = createTransfer(from = newSender())
        awaitState(transfer.id, "COMPLETED")
        val services = listOf("transfer-service", "account-service", "pix-service")

        val byTransferId = services.flatMap { LogFiles.of(it) }.filter { it.contains("transferId=${transfer.id} ") }
        val byCid = services.flatMap { LogFiles.of(it) }.filter { it.contains("cid=TRF-${transfer.id}") }

        assertThat(byTransferId).anyMatch { it.contains("saga CREATED → DEBIT_PENDING") }
        assertThat(byTransferId).anyMatch { it.contains("débito aprovado") }
        assertThat(byTransferId).anyMatch { it.contains("saga PIX_PENDING → COMPLETED") }
        assertThat(byCid).anyMatch { it.contains(".SPI-") && it.contains("SPI liquidou") }
        assertThat(byCid).hasSameSizeAs(byTransferId)
    }

    @Test
    fun `transitions and sagas by state show up in the metrics`() {
        val transfer = createTransfer(from = newSender())
        awaitState(transfer.id, "COMPLETED")

        await atMost Duration.ofSeconds(10) untilAsserted {
            assertThat(metric(8081, "saga.transitions", "to:COMPLETED")).isPositive()
            assertThat(metric(8081, "saga.state", "state:COMPLETED")).isPositive()
        }
    }
}
