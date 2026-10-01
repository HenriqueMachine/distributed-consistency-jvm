package workshop.saga.contracts

import java.util.UUID

/**
 * Uma mensagem e os metadados da entrega que viajam nos headers Kafka: a identidade da
 * mensagem, a simulação pedida e o correlation id.
 */
data class Envelope(
    val messageId: UUID,
    val message: Message,
    val simulation: Simulation? = null,
    val cid: Cid? = null,
) {
    /** A transferência da mensagem: é a chave Kafka. */
    val transferId: Long get() = message.transferId

    /** O tipo que vai no header `messageType`. */
    val type: String get() = MessageCodec.typeOf(message)

    /** A resposta a esta mensagem: mesma simulação e **mesmo cid** do comando recebido. */
    fun reply(message: Message) = of(message, simulation, cid)

    companion object {
        /** Nova mensagem: um [messageId] novo a cada envio. */
        fun of(message: Message, simulation: Simulation? = null, cid: Cid? = null) =
            Envelope(UUID.randomUUID(), message, simulation, cid)
    }
}
