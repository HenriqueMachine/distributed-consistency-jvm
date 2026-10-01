package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.createTransferExpectingFailure
import workshop.saga.e2e.WorkshopClient.newSender
import workshop.saga.e2e.WorkshopClient.statement
import workshop.saga.e2e.WorkshopClient.transfer
import java.time.Duration

/** Cada falha que a apresentação sabe provocar, com o comportamento esperado neste passo. */
class FailureScenariosTest {

    @Test
    fun `quebra passo-2 - CRASH_AFTER_SEND debita uma transferencia que nao existe`() {
        val transferId = createTransferExpectingFailure(from = newSender(), simulate = "CRASH_AFTER_SEND")

        assertThat(transfer(transferId)).isNull()
        await atMost Duration.ofSeconds(15) untilAsserted {
            assertThat(statement(transferId).debits).hasSize(1)
        }
    }
}
