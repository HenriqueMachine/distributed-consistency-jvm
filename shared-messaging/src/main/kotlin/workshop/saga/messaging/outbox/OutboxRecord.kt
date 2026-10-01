package workshop.saga.messaging.outbox

import workshop.saga.contracts.Simulation
import java.util.UUID

/** Uma linha da outbox: a mensagem pronta para sair, com tudo o que o Kafka precisa. */
data class OutboxRecord(
    val messageId: UUID,
    val topic: String,
    val key: String,
    val type: String,
    val payload: String,
    val simulation: Simulation?,
)
