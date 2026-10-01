package workshop.saga.transfer.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.Saga
import workshop.saga.transfer.domain.saga.SagaState
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/** Tabela `sagas`: o estado operacional atual de cada transferência (estado, prazo e tentativas). */
@Repository
class SagaRepository(private val jdbc: JdbcClient) {

    /** Grava a saga de uma transferência nova. */
    fun insert(saga: Saga) {
        jdbc.sql(
            """
            insert into sagas (transfer_id, state, deadline_at, attempts)
            values (:transferId, :state, :deadlineAt, :attempts)
            """,
        )
            .param("transferId", saga.transferId.value)
            .param("state", saga.state.name)
            .param("deadlineAt", saga.deadlineAt?.let(Timestamp::from))
            .param("attempts", saga.attempts)
            .update()
    }

    /** Grava o novo estado, prazo e tentativas da saga. */
    fun update(saga: Saga) {
        jdbc.sql(
            """
            update sagas
            set state = :state, deadline_at = :deadlineAt, attempts = :attempts, updated_at = now()
            where transfer_id = :transferId
            """,
        )
            .param("transferId", saga.transferId.value)
            .param("state", saga.state.name)
            .param("deadlineAt", saga.deadlineAt?.let(Timestamp::from))
            .param("attempts", saga.attempts)
            .update()
    }

    /** A saga, sem travar a linha (leitura). */
    fun findByTransferId(transferId: TransferId): Saga? = selectByTransferId(transferId, lock = false)

    /** Lê a saga e trava a linha até o fim da transação. */
    fun lockByTransferId(transferId: TransferId): Saga? = selectByTransferId(transferId, lock = true)

    /** Transferências cuja saga passou do prazo, as mais atrasadas primeiro. */
    fun findOverdue(now: Instant, limit: Int): List<TransferId> =
        jdbc.sql("select transfer_id from sagas where deadline_at <= :now order by deadline_at limit :limit")
            .param("now", Timestamp.from(now))
            .param("limit", limit)
            .query { rs, _ -> TransferId(rs.getLong("transfer_id")) }
            .list()

    private fun selectByTransferId(transferId: TransferId, lock: Boolean): Saga? =
        jdbc.sql(
            "select transfer_id, state, deadline_at, attempts from sagas where transfer_id = :transferId" +
                if (lock) " for update" else "",
        )
            .param("transferId", transferId.value)
            .query { rs, _ -> rs.toSaga() }
            .optional()
            .orElse(null)

    private fun ResultSet.toSaga() = Saga(
        transferId = TransferId(getLong("transfer_id")),
        state = SagaState.valueOf(getString("state")),
        deadlineAt = getTimestamp("deadline_at")?.toInstant(),
        attempts = getInt("attempts"),
    )
}
