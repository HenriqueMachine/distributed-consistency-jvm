package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.awaitState
import workshop.saga.e2e.WorkshopClient.createTransfer
import workshop.saga.e2e.WorkshopClient.newSender
import workshop.saga.e2e.WorkshopClient.transitions

/** O log de transições (slide 23): cada mudança de estado é um fato registrado. */
class TransitionsTest {

    @Test
    fun `cada transicao vira uma linha com motivo, evento, cid e versao do codigo`() {
        val transfer = createTransfer(from = newSender())
        awaitState(transfer.id, "COMPLETED")

        val history = transitions(transfer.id)

        assertThat(history.map { "${it.from} → ${it.to}" }).containsExactly(
            "CREATED → DEBIT_PENDING",
            "DEBIT_PENDING → PIX_PENDING",
            "PIX_PENDING → COMPLETED",
        )
        assertThat(history.first().eventId).isNull()
        assertThat(history.drop(1)).allSatisfy { assertThat(it.eventId).isNotNull() }
        // A criação é causada pela própria transferência (cid raiz); as demais, pelas respostas,
        // que carregam o cid do comando que as originou (slide 41).
        assertThat(history.first().cid).isEqualTo("TRF-${transfer.id}")
        assertThat(history[1].cid).startsWith("TRF-${transfer.id}.DEB-")
        assertThat(history[2].cid).startsWith("TRF-${transfer.id}.PIX-")
        assertThat(history).allSatisfy {
            assertThat(it.appVersion).isNotBlank()
            assertThat(it.reason).isNotBlank()
        }
    }
}
