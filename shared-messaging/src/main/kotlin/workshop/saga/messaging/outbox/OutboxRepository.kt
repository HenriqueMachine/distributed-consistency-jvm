package workshop.saga.messaging.outbox

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.contracts.Envelope
import workshop.saga.contracts.MessageCodec
import workshop.saga.contracts.Simulation
import workshop.saga.contracts.Topics
import java.util.UUID

/** Tabela `outbox` do serviço. Cada serviço tem a sua, no próprio banco. */
@Repository
class OutboxRepository(private val jdbc: JdbcClient) {

    /** Grava a mensagem para o relay publicar. */
    fun save(envelope: Envelope) {
        jdbc.sql(
            """
            insert into outbox (id, topic, message_key, message_type, payload, simulate)
            values (:id, :topic, :key, :type, :payload, :simulate)
            """,
        )
            .param("id", envelope.messageId)
            .param("topic", Topics.of(envelope.message))
            .param("key", envelope.transferId.toString())
            .param("type", envelope.type)
            .param("payload", MessageCodec.encode(envelope.message))
            .param("simulate", envelope.simulation?.name)
            .update()
    }

    /**
     * Linhas ainda não publicadas, na ordem em que foram gravadas. `skip locked`: se houver
     * mais de uma instância do serviço, cada relay pega linhas diferentes.
     */
    fun lockPending(limit: Int): List<OutboxRecord> =
        jdbc.sql(
            """
            select id, topic, message_key, message_type, payload, simulate
            from outbox
            where published_at is null
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
