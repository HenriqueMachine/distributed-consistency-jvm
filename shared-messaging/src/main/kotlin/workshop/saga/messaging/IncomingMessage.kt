package workshop.saga.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import workshop.saga.contracts.Envelope
import workshop.saga.contracts.InvalidPayloadException
import workshop.saga.contracts.MessageCodec
import workshop.saga.contracts.MessageHeaders
import workshop.saga.contracts.Simulation
import java.util.UUID

/** Traduz um registro Kafka de volta para um [Envelope], lendo payload e headers. */
object IncomingMessage {

    /**
     * Lê payload, tipo, `messageId` e simulação de [record].
     * @throws InvalidPayloadException se faltar o `messageId` ou o payload não servir.
     */
    fun read(record: ConsumerRecord<String, String>): Envelope {
        val type = record.header(MessageHeaders.MESSAGE_TYPE)
        val messageId = record.header(MessageHeaders.MESSAGE_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw InvalidPayloadException("mensagem sem ${MessageHeaders.MESSAGE_ID} válido no offset ${record.offset()}")
        return Envelope(
            messageId = messageId,
            message = MessageCodec.decode(type, record.value()),
            simulation = runCatching { Simulation.parse(record.header(MessageHeaders.SIMULATE)) }.getOrNull(),
        )
    }

    private fun ConsumerRecord<String, String>.header(name: String): String? =
        headers().lastHeader(name)?.value()?.toString(Charsets.UTF_8)
}
