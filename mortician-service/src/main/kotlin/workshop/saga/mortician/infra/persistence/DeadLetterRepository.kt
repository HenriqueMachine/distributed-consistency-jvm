package workshop.saga.mortician.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.mortician.domain.DeadLetter
import workshop.saga.mortician.domain.DeadLetterStatus
import workshop.saga.mortician.domain.IncomingDeadLetter
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Tabela `dead_letters`. Cada registro de DLT é único por tópico, partição e offset: aqui
 * o offset basta como chave de idempotência, porque ninguém republica na DLT.
 */
@Repository
class DeadLetterRepository(private val jdbc: JdbcClient) {

    /** Guarda a mensagem e devolve o id; nulo se ela já estava guardada. */
    fun insertIfAbsent(dead: IncomingDeadLetter): Long? =
        jdbc.sql(
            """
            insert into dead_letters (dlt_topic, dlt_partition, dlt_offset, original_topic, message_key,
                                      message_id, message_type, payload, error, transfer_id, status)
            values (:dltTopic, :partition, :offset, :originalTopic, :key,
                    :messageId, :messageType, :payload, :error, :transferId, 'NEW')
            on conflict (dlt_topic, dlt_partition, dlt_offset) do nothing
            returning id
            """,
        )
            .param("dltTopic", dead.deadLetterTopic)
            .param("partition", dead.partition)
            .param("offset", dead.offset)
            .param("originalTopic", dead.originalTopic)
            .param("key", dead.key)
            .param("messageId", dead.messageId)
            .param("messageType", dead.messageType)
            .param("payload", dead.payload)
            .param("error", dead.error)
            .param("transferId", dead.transferId)
            .query(Long::class.java)
            .optional()
            .orElse(null)

    /** Mensagens mortas filtradas por status e transferência, das mais novas para as mais antigas. */
    fun find(status: DeadLetterStatus?, transferId: Long?): List<DeadLetter> =
        jdbc.sql(
            """
            select * from dead_letters
            where (cast(:status as varchar) is null or status = :status)
              and (cast(:transferId as bigint) is null or transfer_id = :transferId)
            order by id desc
            """,
        )
            .param("status", status?.name)
            .param("transferId", transferId)
            .query { rs, _ -> rs.toDeadLetter() }
            .list()

    /** Uma mensagem morta, ou nula. */
    fun findById(id: Long): DeadLetter? =
        jdbc.sql("select * from dead_letters where id = :id")
            .param("id", id)
            .query { rs, _ -> rs.toDeadLetter() }
            .optional()
            .orElse(null)

    /**
     * Grava o resgate (status, quem, por quê e quando) só se a mensagem ainda estiver `NEW`.
     * Devolve `false` se alguém resgatou antes: dois resgates simultâneos não passam juntos.
     */
    fun markRepublished(dead: DeadLetter): Boolean {
        val rescue = checkNotNull(dead.rescue) { "dead letter ${dead.id} sem resgate" }
        return jdbc.sql(
            """
            update dead_letters
            set status = :status, republished_by = :by, republish_reason = :reason, republished_at = :at
            where id = :id and status = 'NEW'
            """,
        )
            .param("id", dead.id)
            .param("status", dead.status.name)
            .param("by", rescue.by)
            .param("reason", rescue.reason)
            .param("at", Timestamp.from(rescue.at))
            .update() == 1
    }

    private fun ResultSet.toDeadLetter() = DeadLetter(
        id = getLong("id"),
        deadLetterTopic = getString("dlt_topic"),
        originalTopic = getString("original_topic"),
        key = getString("message_key"),
        messageId = getObject("message_id", UUID::class.java),
        messageType = getString("message_type"),
        payload = getString("payload"),
        error = getString("error"),
        transferId = getLong("transfer_id").takeUnless { wasNull() },
        status = DeadLetterStatus.valueOf(getString("status")),
        receivedAt = getTimestamp("received_at").toInstant(),
        rescue = getString("republished_by")?.let {
            DeadLetter.Rescue(it, getString("republish_reason"), getTimestamp("republished_at").toInstant())
        },
    )
}
