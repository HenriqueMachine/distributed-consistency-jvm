package workshop.saga.messaging.outbox

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.contracts.Cid
import workshop.saga.contracts.MessageCodec
import workshop.saga.contracts.SagaMessage
import workshop.saga.contracts.Simulation
import workshop.saga.contracts.Topics
import java.time.Duration
import java.util.UUID

/** Tabela `outbox` do serviço. Cada serviço tem a sua, no próprio banco. */
@Repository
class OutboxRepository(private val jdbc: JdbcClient) {

    /** Grava a mensagem para o relay publicar a partir de agora + [delay]. */
    fun save(sagaMessage: SagaMessage, delay: Duration = Duration.ZERO) =
        save(
            OutboxRecord(
                messageId = sagaMessage.messageId,
                topic = Topics.of(sagaMessage.message),
                key = sagaMessage.transferId.toString(),
                type = sagaMessage.type,
                payload = MessageCodec.encode(sagaMessage.message),
                simulation = sagaMessage.simulation,
                cid = sagaMessage.cid,
            ),
            delay,
        )

    /**
     * Grava uma mensagem já pronta (tópico, chave, tipo e payload crus). É o que o Mortician
     * usa para republicar uma mensagem da DLT no tópico original (passo 7).
     */
    fun save(record: OutboxRecord, delay: Duration = Duration.ZERO) {
        jdbc.sql(
            """
            insert into outbox (id, topic, message_key, message_type, payload, simulate, cid, available_at)
            values (:id, :topic, :key, :type, :payload, :simulate, :cid, now() + make_interval(secs => :delaySeconds))
            """,
        )
            .param("id", record.messageId)
            .param("topic", record.topic)
            .param("key", record.key)
            .param("type", record.type)
            .param("payload", record.payload)
            .param("simulate", record.simulation?.name)
            .param("cid", record.cid?.value)
            .param("delaySeconds", delay.toMillis() / 1000.0)
            .update()
    }

    /**
     * Linhas prontas e ainda não publicadas, na ordem em que foram gravadas. `skip locked`: se houver
     * mais de uma instância do serviço, cada relay pega linhas diferentes.
     */
    fun lockPending(limit: Int): List<OutboxRecord> =
        jdbc.sql(
            """
            select id, topic, message_key, message_type, payload, simulate, cid
            from outbox
            where published_at is null and available_at <= now()
            order by created_at
            limit :limit
            for update skip locked
            """,
        )
            .param("limit", limit)
            .query { rs, _ ->
                OutboxRecord(
                    messageId = rs.getObject("id", UUID::class.java),
                    topic = rs.getString("topic"),
                    key = rs.getString("message_key"),
                    type = rs.getString("message_type"),
                    payload = rs.getString("payload"),
                    simulation = rs.getString("simulate")?.let(Simulation::valueOf),
                    cid = rs.getString("cid")?.let(::Cid),
                )
            }
            .list()

    /** Marca as linhas como publicadas. */
    fun markPublished(messageIds: List<UUID>) {
        if (messageIds.isEmpty()) return
        jdbc.sql("update outbox set published_at = now() where id in (:ids)")
            .param("ids", messageIds)
            .update()
    }
}
