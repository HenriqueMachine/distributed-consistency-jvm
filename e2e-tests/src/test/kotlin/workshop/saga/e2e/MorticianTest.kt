package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.awaitState
import workshop.saga.e2e.WorkshopClient.createTransfer
import workshop.saga.e2e.WorkshopClient.deadLetters
import workshop.saga.e2e.WorkshopClient.newSender
import workshop.saga.e2e.WorkshopClient.pixCredits
import workshop.saga.e2e.WorkshopClient.republish
import java.time.Duration

/** Passo 7 (slide 43): a DLT ganha um dono, e a transferência parada pode ser resgatada. */
class MorticianTest {

    @Test
    fun `PIX_CRASH - o Mortician guarda as mensagens mortas e o resgate conclui a transferencia`() {
        val transfer = createTransfer(from = newSender(), simulate = "PIX_CRASH")
        awaitState(transfer.id, "NEEDS_ATTENTION", timeout = Duration.ofSeconds(60))

        await atMost Duration.ofSeconds(15) untilAsserted { assertThat(deadLetters(transfer.id)).hasSize(3) }
        val dead = deadLetters(transfer.id)
        assertThat(dead).allSatisfy {
            assertThat(it.status).isEqualTo("NEW")
            assertThat(it.originalTopic).isEqualTo("pix.commands")
            assertThat(it.messageType).isEqualTo("SendPix")
            assertThat(it.error).contains("PixProcessingException")
        }

        assertThat(republish(dead.first().id, reason = "bug do PIX_CRASH corrigido")).isEqualTo(200)

        awaitState(transfer.id, "COMPLETED")
        assertThat(pixCredits(transfer.id)).hasSize(1)
        val rescued = deadLetters(transfer.id).single { it.id == dead.first().id }
        assertThat(rescued.status).isEqualTo("REPUBLISHED")
        assertThat(rescued.republishReason).isEqualTo("bug do PIX_CRASH corrigido")
        assertThat(republish(dead.first().id, reason = "de novo")).isEqualTo(409)
    }

    @Test
    fun `as linhas de retry e de DLT dizem de qual transferencia sao`() {
        val transfer = createTransfer(from = newSender(), simulate = "PIX_CRASH")
        await atMost Duration.ofSeconds(20) untilAsserted { assertThat(deadLetters(transfer.id)).isNotEmpty() }

        val lines = LogFiles.of("pix-service").filter { it.contains("transferId=${transfer.id} ") }

        assertThat(lines).anyMatch { it.contains("Record in retry and not yet recovered") }
        assertThat(lines).anyMatch { it.contains("falha tentativa=3 → pix.commands.DLT") }
    }
}
