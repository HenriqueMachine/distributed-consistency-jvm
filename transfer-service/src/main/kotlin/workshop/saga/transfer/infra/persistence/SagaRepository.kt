package workshop.saga.transfer.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.Saga
import workshop.saga.transfer.domain.saga.SagaState

/** Tabela `sagas`: uma linha por transferência com o estado atual do processo. */
@Repository
class SagaRepository(private val jdbc: JdbcClient) {

    /** Grava a saga de uma transferência nova. */
    fun insert(saga: Saga) {
        jdbc.sql("insert into sagas (transfer_id, state) values (:transferId, :state)")
            .param("transferId", saga.transferId.value)
            .param("state", saga.state.name)
            .update()
    }

    /** A saga da transferência, ou nulo. */
    fun findByTransferId(transferId: TransferId): Saga? =
        jdbc.sql("select transfer_id, state from sagas where transfer_id = :transferId")
            .param("transferId", transferId.value)
            .query { rs, _ -> Saga(TransferId(rs.getLong("transfer_id")), SagaState.valueOf(rs.getString("state"))) }
            .optional()
            .orElse(null)
}
