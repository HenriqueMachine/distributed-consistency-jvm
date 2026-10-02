package workshop.saga.pix.infra.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import workshop.saga.contracts.InvalidPayloadException
import workshop.saga.contracts.SendPix
import workshop.saga.contracts.Topics
import workshop.saga.messaging.IncomingMessage
import workshop.saga.pix.application.PixService

/**
 * Recebe os comandos do orquestrador e os entrega ao caso de uso.
 *
 * O `id` do listener é o nome do container que o circuit breaker do SPI pausa e retoma
 * ([workshop.saga.pix.infra.spi.SpiCircuitBreakerListener]).
 */
@Component
class PixCommandListener(private val pixService: PixService) {

    /** Um `SendPix`. */
    @KafkaListener(id = LISTENER_ID, idIsGroup = false, topics = [Topics.PIX_COMMANDS])
    fun onCommand(record: ConsumerRecord<String, String>) {
        val sagaMessage = IncomingMessage.read(record)
        when (val command = sagaMessage.message) {
            is SendPix -> pixService.send(command, sagaMessage)
            else -> throw InvalidPayloadException("${sagaMessage.type} não é um comando de Pix")
        }
    }

    companion object {
        /** Nome do container do listener, para pausar e retomar. */
        const val LISTENER_ID = "pix"
    }
}
