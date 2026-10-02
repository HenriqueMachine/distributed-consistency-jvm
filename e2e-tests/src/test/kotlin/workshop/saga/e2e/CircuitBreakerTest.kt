package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.awaitState
import workshop.saga.e2e.WorkshopClient.createTransfer
import workshop.saga.e2e.WorkshopClient.endSpiOutage
import workshop.saga.e2e.WorkshopClient.newSender
import workshop.saga.e2e.WorkshopClient.pixCredits
import workshop.saga.e2e.WorkshopClient.spiCircuitState
import workshop.saga.e2e.WorkshopClient.startSpiOutage
import java.time.Duration

/** Passo 6 (slide 39): o SPI cai para todos, o circuito abre, e nada vai para a DLT. */
class CircuitBreakerTest {

    @AfterEach
    fun spiBackOnline() {
        endSpiOutage()
        await atMost Duration.ofSeconds(30) untilAsserted { assertThat(spiCircuitState()).isEqualTo("CLOSED") }
    }

    @Test
    fun `SPI fora do ar - o circuito abre, o consumidor pausa e a transferencia conclui quando o SPI volta`() {
        startSpiOutage(seconds = 15)

        val transfer = createTransfer(from = newSender())

        await atMost Duration.ofSeconds(20) untilAsserted { assertThat(spiCircuitState()).isEqualTo("OPEN") }
        awaitState(transfer.id, "COMPLETED", timeout = Duration.ofSeconds(45))
        assertThat(pixCredits(transfer.id)).hasSize(1)
        assertThat(WorkshopKafka.recordsWithKey("pix.commands.DLT", transfer.id.toString())).isEmpty()
        assertThat(LogFiles.of("pix-service")).anyMatch { it.contains("circuito spi CLOSED → OPEN: pausando o consumidor pix") }
    }
}
