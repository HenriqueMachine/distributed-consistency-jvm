package workshop.saga.messaging

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.Envelope
import workshop.saga.messaging.outbox.OutboxRepository

/**
 * Publica mensagens da saga pela outbox (slide 25).
 *
 * "Publicar" agora é gravar uma linha na tabela `outbox`, **na mesma transação** do dado de
 * negócio: ou os dois são gravados, ou nenhum. Quem leva a linha até o Kafka é o
 * [outbox.OutboxRelay], depois do commit.
 *
 * `MANDATORY`: chamar fora de uma transação é erro de programação, e falha na hora.
 */
@Component
class MessagePublisher(private val outbox: OutboxRepository) {

    /** Grava [envelope] na outbox, na transação de quem chama. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun publish(envelope: Envelope) {
        outbox.save(envelope)
    }
}
