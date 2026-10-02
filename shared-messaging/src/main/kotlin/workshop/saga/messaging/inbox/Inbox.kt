package workshop.saga.messaging.inbox

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.SagaMessage

/**
 * Idempotência, camada 1 (slide 35): "já processei este messageId?".
 *
 * Registra o `messageId` em `processed_messages` **na mesma transação do efeito**. Se o
 * efeito falhar, o registro também volta, e a mensagem pode ser reprocessada. Se a
 * linha já existir, é duplicata: loga, ignora, e o offset é commitado normalmente.
 */
@Component
class Inbox(private val jdbc: JdbcClient) {

    /** `true` na primeira entrega desta mensagem; `false` nas repetições. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun firstDelivery(sagaMessage: SagaMessage): Boolean {
        val inserted = jdbc.sql(
            """
            insert into processed_messages (message_id, message_type)
            values (:messageId, :type)
            on conflict (message_id) do nothing
            """,
        )
            .param("messageId", sagaMessage.messageId)
            .param("type", sagaMessage.type)
            .update() == 1
        if (!inserted) {
            log.info(
                "evento {}… já processado — ignorado{}",
                sagaMessage.messageId.toString().take(4),
                sagaMessage.simulation?.let { " (simulate=$it)" } ?: "",
            )
        }
        return inserted
    }

    private companion object {
        val log = LoggerFactory.getLogger(Inbox::class.java)
    }
}
