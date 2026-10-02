package workshop.saga.contracts

import java.util.UUID

/**
 * Uma mensagem da saga como ela circula entre os serviços: o conteúdo ([message]) mais o que
 * viaja nos headers Kafka. O [messageId] é a identidade da entrega (idempotência, camada 1),
 * o [cid] é o correlation id e a [simulation] é a falha pedida pela apresentação.
 */
data class SagaMessage(
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
            SagaMessage(UUID.randomUUID(), message, simulation, cid)
    }
}
