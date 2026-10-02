package workshop.saga.messaging.inbox

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.Envelope

/**
 * Idempotência, camada 1 (slide 34): "já processei este messageId?".
 *
 * Registra o `messageId` em `processed_messages` **na mesma transação do efeito**. Se o
 * efeito falhar, o registro também volta, e a mensagem pode ser reprocessada. Se a
 * linha já existir, é duplicata: loga, ignora, e o offset é commitado normalmente.
 */
@Component
class Inbox(private val jdbc: JdbcClient) {

    /** `true` na primeira entrega desta mensagem; `false` nas repetições. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun firstDelivery(envelope: Envelope): Boolean {
        val inserted = jdbc.sql(
            """
            insert into processed_messages (message_id, message_type)
            values (:messageId, :type)
            on conflict (message_id) do nothing
            """,
        )
            .param("messageId", envelope.messageId)
            .param("type", envelope.type)
            .update() == 1
        if (!inserted) {
            log.info(
                "evento {}… já processado — ignorado{}",
                envelope.messageId.toString().take(4),
                envelope.simulation?.let { " (simulate=$it)" } ?: "",
            )
        }
        return inserted
    }

    private companion object {
        val log = LoggerFactory.getLogger(Inbox::class.java)
    }
}
