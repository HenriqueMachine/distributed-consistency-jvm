package workshop.saga.transfer.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.SagaState
import workshop.saga.transfer.domain.saga.SagaTransition
import java.util.UUID

/**
 * Tabela `saga_transitions` (slide 23): só de inserção. Não há `update` nem `delete` aqui, e
 * o próprio banco os proíbe (`revoke update, delete`): fatos não se apagam.
 */
@Repository
class SagaTransitionRepository(private val jdbc: JdbcClient) {

    /** Acrescenta um fato ao histórico da saga. */
    fun append(transition: SagaTransition) {
        jdbc.sql(
            """
            insert into saga_transitions (transfer_id, from_status, to_status, reason, event_id, cid, app_version)
            values (:transferId, :from, :to, :reason, :eventId, :cid, :appVersion)
            """,
        )
            .param("transferId", transition.transferId.value)
            .param("from", transition.from.name)
            .param("to", transition.to.name)
            .param("reason", transition.reason)
            .param("eventId", transition.eventId)
            .param("cid", transition.cid)
            .param("appVersion", transition.appVersion)
            .update()
    }

    /** O histórico da saga, do primeiro fato ao último. */
    fun findByTransferId(transferId: TransferId): List<SagaTransition> =
        jdbc.sql(
            """
            select transfer_id, from_status, to_status, reason, event_id, cid, app_version
            from saga_transitions where transfer_id = :transferId order by id
            """,
        )
            .param("transferId", transferId.value)
            .query { rs, _ ->
                SagaTransition(
                    transferId = TransferId(rs.getLong("transfer_id")),
                    from = SagaState.valueOf(rs.getString("from_status")),
                    to = SagaState.valueOf(rs.getString("to_status")),
                    reason = rs.getString("reason"),
                    eventId = rs.getObject("event_id", UUID::class.java),
                    cid = rs.getString("cid"),
                    appVersion = rs.getString("app_version"),
                )
            }
            .list()
}
