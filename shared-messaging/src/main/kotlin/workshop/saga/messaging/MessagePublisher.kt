package workshop.saga.messaging

import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import workshop.saga.contracts.Envelope
import workshop.saga.contracts.MessageCodec
import workshop.saga.contracts.MessageHeaders
import workshop.saga.contracts.Topics

/**
 * Publica mensagens da saga no Kafka.
 *
 * ⚠ QUEBRA passo-2: o envio acontece **dentro** da transação do banco de quem chama, mas o
 * Kafka não participa dessa transação. Se o commit falhar depois do `send`, a mensagem já
 * saiu (cenário A do slide 24); se a aplicação cair entre o commit e o `send`, ela nunca
 * sai (cenário B). O passo 3 troca o envio direto pela outbox.
 */
@Component
class MessagePublisher(private val kafka: KafkaTemplate<String, String>) {

    /** Envia [envelope] ao tópico da mensagem, com chave = transferId, e espera o ack. */
    fun publish(envelope: Envelope) {
        val record = ProducerRecord<String, String>(
            Topics.of(envelope.message),
            envelope.transferId.toString(),
            MessageCodec.encode(envelope.message),
        ).apply {
            headers().add(MessageHeaders.MESSAGE_ID, envelope.messageId.toString().toByteArray())
            headers().add(MessageHeaders.MESSAGE_TYPE, envelope.type.toByteArray())
            envelope.simulation?.let { headers().add(MessageHeaders.SIMULATE, it.name.toByteArray()) }
        }
        // .get(): espera o ack do broker, para que um erro de envio apareça aqui e não se perca.
        kafka.send(record).get()
    }
}
