package workshop.saga.messaging

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.Envelope
import workshop.saga.messaging.outbox.OutboxRepository
import java.time.Duration

/**
 * Publica mensagens da saga pela outbox (slide 32).
 *
 * "Publicar" agora é gravar uma linha na tabela `outbox`, **na mesma transação** do dado de
 * negócio: ou os dois são gravados, ou nenhum. Quem leva a linha até o Kafka é o
 * [outbox.OutboxRelay], depois do commit.
 *
 * `MANDATORY`: chamar fora de uma transação é erro de programação, e falha na hora.
 */
@Component
class MessagePublisher(private val outbox: OutboxRepository) {

    /**
     * Grava [envelope] na outbox, na transação de quem chama. [delay] segura a mensagem na
     * outbox antes de o relay publicá-la (usado pelo `DEBIT_SLOW`).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun publish(envelope: Envelope, delay: Duration = Duration.ZERO) {
        outbox.save(envelope, delay)
    }
}
