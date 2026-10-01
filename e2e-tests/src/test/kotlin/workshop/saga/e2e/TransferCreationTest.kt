package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.during
import org.awaitility.kotlin.until
import org.junit.jupiter.api.Test
import java.time.Duration

class TransferCreationTest {

    @Test
    fun `POST transfers grava a transferencia com a saga em CREATED`() {
        val created = WorkshopClient.createTransfer(from = "ana", to = "henrique", amount = "150.00")

        assertThat(created.id).isGreaterThanOrEqualTo(1042)
        assertThat(created.state).isEqualTo("CREATED")
        assertThat(WorkshopClient.transfer(created.id)?.amount).isEqualByComparingTo("150.00")
    }

    @Test
    fun `quebra passo-1 - ninguem conversa ainda, a transferencia fica parada em CREATED`() {
        val created = WorkshopClient.createTransfer()

        await during Duration.ofSeconds(3) atMost Duration.ofSeconds(5) until {
            WorkshopClient.transfer(created.id)?.state == "CREATED"
        }
    }
}
