package workshop.saga.pix.infra.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import workshop.saga.contracts.InvalidPayloadException
import workshop.saga.contracts.SendPix
import workshop.saga.contracts.Topics
import workshop.saga.messaging.IncomingMessage
import workshop.saga.pix.application.PixService

/** Recebe os comandos do orquestrador e os entrega ao caso de uso. */
@Component
class PixCommandListener(private val pixService: PixService) {

    /** Um `SendPix`. */
    @KafkaListener(topics = [Topics.PIX_COMMANDS])
    fun onCommand(record: ConsumerRecord<String, String>) {
        val envelope = IncomingMessage.read(record)
        when (val command = envelope.message) {
            is SendPix -> pixService.send(command, envelope)
            else -> throw InvalidPayloadException("${envelope.type} não é um comando de Pix")
        }
    }
}
