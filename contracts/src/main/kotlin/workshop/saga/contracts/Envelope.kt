package workshop.saga.contracts

import java.util.UUID

/** Uma mensagem e os metadados da entrega que viajam nos headers Kafka. */
data class Envelope(
    val messageId: UUID,
    val message: Message,
    val simulation: Simulation? = null,
) {
    /** A transferência da mensagem: é a chave Kafka. */
    val transferId: Long get() = message.transferId

    /** O tipo que vai no header `messageType`. */
    val type: String get() = MessageCodec.typeOf(message)

    companion object {
        /** Nova mensagem: um [messageId] novo a cada envio. */
        fun of(message: Message, simulation: Simulation? = null) =
            Envelope(UUID.randomUUID(), message, simulation)
    }
}
